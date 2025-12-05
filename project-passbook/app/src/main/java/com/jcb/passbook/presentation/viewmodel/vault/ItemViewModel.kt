package com.jcb.passbook.presentation.viewmodel.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.presentation.viewmodel.vault.ItemOperationState
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ItemViewModel"

// ✅ REMOVED: Duplicate sealed class ItemOperationState (using import instead)

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val cryptoManager: CryptoManager,
    private val auditLogger: AuditLogger,
    private val userDao: UserDao,
    private val db: AppDatabase
) : ViewModel() {

    // ══════════════════════════════════════════════════════════════
    // State Management
    // ══════════════════════════════════════════════════════════════

    private val _userId = MutableStateFlow(-1L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    /**
     * ✅ CRITICAL FIX: Items are loaded reactively based on userId
     * All database operations happen on IO dispatcher automatically via Flow
     */
    val items: StateFlow<List<Item>> = _userId
        .flatMapLatest { id ->
            if (id != -1L) {
                repository.getItemsForUser(id)
            } else {
                flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.IO) // ✅ Ensure IO dispatcher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationState = MutableStateFlow<ItemOperationState>(ItemOperationState.Idle)
    val operationState: StateFlow<ItemOperationState> = _operationState.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════

    /**
     * Set the current user ID to load their items
     */
    fun setUserId(userId: Long) {
        Timber.tag(TAG).i("setUserId called with: $userId (previous: ${_userId.value})")
        _userId.value = userId
        Timber.tag(TAG).i("userId successfully updated to: ${_userId.value}")
    }

    /**
     * Clear all items by resetting userId
     */
    fun clearAllItems() {
        _userId.value = -1L
    }

    /**
     * Reset operation state to idle
     */
    fun clearOperationState() {
        _operationState.value = ItemOperationState.Idle
    }

    // ══════════════════════════════════════════════════════════════
    // ✅ CRITICAL FIX: All Database Operations on IO Dispatcher
    // ══════════════════════════════════════════════════════════════

    /**
     * Insert a new password item with encryption
     * ✅ FIXED: All operations on Dispatchers.IO
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun insert(
        itemName: String,
        plainTextPassword: String,
        username: String? = null,
        url: String? = null,
        notes: String? = null,
        categoryId: Long? = null,
        categoryName: String? = null
    ) {
        val currentUserId: Long = _userId.value
        Timber.tag(TAG).d("Insert called - itemName: $itemName, currentUserId: $currentUserId")

        // ✅ VALIDATION 1: Check userId on main thread (fast check)
        if (currentUserId == -1L) {
            Timber.tag(TAG).e("Insert BLOCKED - No user ID set!")
            _operationState.value = ItemOperationState.Error("No user ID set. Please logout and login again.")
            return
        }

        Timber.tag(TAG).i("✓ Insert validation passed - proceeding for user: $currentUserId")
        _operationState.value = ItemOperationState.Loading

        // ✅ CRITICAL FIX: Launch on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // ✅ Database check on IO thread
                if (!db.isOpen) {
                    throw IllegalStateException("Database closed during operation")
                }

                // ✅ Encryption on IO thread
                val encryptedData = cryptoManager.encrypt(plainTextPassword)

                val newItem = Item(
                    title = itemName.trim(),
                    encryptedPassword = encryptedData,
                    userId = currentUserId,
                    username = username?.trim(),
                    url = url?.trim(),
                    notes = notes?.trim(),
                    categoryId = categoryId,
                    categoryName = categoryName,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                Timber.tag(TAG).d("Inserting item into repository...")
                repository.insert(newItem)
                Timber.tag(TAG).i("✓ Item inserted successfully: $itemName")

                // ✅ Audit logging on IO thread
                val user = userDao.getUser(currentUserId)
                auditLogger.logDataAccess(
                    userId = currentUserId,
                    username = user?.username ?: "Unknown",
                    action = "Created password item: $itemName",
                    resourceType = "ITEM",
                    resourceId = newItem.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                // ✅ Update UI state on Main thread
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Success
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to insert item: $itemName")

                // ✅ Try to log audit failure (still on IO)
                try {
                    if (db.isOpen) {
                        val user = userDao.getUser(currentUserId)
                        auditLogger.logDataAccess(
                            userId = currentUserId,
                            username = user?.username ?: "Unknown",
                            action = "Failed to create password item: $itemName",
                            resourceType = "ITEM",
                            resourceId = "UNKNOWN",
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = e.localizedMessage
                        )
                    }
                } catch (logError: Exception) {
                    Timber.tag(TAG).w(logError, "Could not log audit - database unavailable")
                }

                // ✅ Update UI state on Main thread
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Error(
                        "Failed to insert item: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    /**
     * Update an existing item
     * ✅ FIXED: All operations on Dispatchers.IO
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun update(
        item: Item,
        newName: String? = null,
        newPlainTextPassword: String? = null,
        newUsername: String? = null,
        newUrl: String? = null,
        newNotes: String? = null,
        newCategoryId: Long? = null,
        newCategoryName: String? = null
    ) {
        Timber.tag(TAG).d("Update called for item: ${item.title}")

        _operationState.value = ItemOperationState.Loading

        // ✅ CRITICAL FIX: Launch on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // ✅ Database check on IO thread
                if (!db.isOpen) {
                    throw IllegalStateException("Database not available")
                }

                val updatedTitle = newName?.trim() ?: item.title
                val updatedUsername = newUsername?.trim() ?: item.username
                val updatedUrl = newUrl?.trim() ?: item.url
                val updatedNotes = newNotes?.trim() ?: item.notes
                val updatedCategoryId = newCategoryId ?: item.categoryId
                val updatedCategoryName = newCategoryName ?: item.categoryName

                // ✅ Encryption on IO thread
                val updatedPassword = newPlainTextPassword?.let {
                    cryptoManager.encrypt(it)
                } ?: item.encryptedPassword

                val updatedItem = item.copy(
                    title = updatedTitle,
                    encryptedPassword = updatedPassword,
                    username = updatedUsername,
                    url = updatedUrl,
                    notes = updatedNotes,
                    categoryId = updatedCategoryId,
                    categoryName = updatedCategoryName,
                    updatedAt = System.currentTimeMillis()
                )

                repository.update(updatedItem)
                Timber.tag(TAG).i("✓ Item updated successfully: ${item.title}")

                // ✅ Audit logging on IO thread
                val user = userDao.getUser(item.userId)
                val actionDetails = buildString {
                    append("Updated password item: ${item.title}")
                    if (newName != null) append(" (renamed to: $updatedTitle)")
                    if (newPlainTextPassword != null) append(" (password changed)")
                }

                auditLogger.logDataAccess(
                    userId = item.userId,
                    username = user?.username ?: "Unknown",
                    action = actionDetails,
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                // ✅ Update UI state on Main thread
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Success
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to update item")

                // ✅ Try to log audit failure (still on IO)
                try {
                    if (db.isOpen) {
                        val user = userDao.getUser(item.userId)
                        auditLogger.logDataAccess(
                            userId = item.userId,
                            username = user?.username ?: "Unknown",
                            action = "Failed to update password item: ${item.title}",
                            resourceType = "ITEM",
                            resourceId = item.id.toString(),
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = e.localizedMessage
                        )
                    }
                } catch (logError: Exception) {
                    Timber.tag(TAG).w(logError, "Could not log audit")
                }

                // ✅ Update UI state on Main thread
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Error(
                        "Failed to update item: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    /**
     * Delete an item
     * ✅ FIXED: All operations on Dispatchers.IO
     */
    fun delete(item: Item) {
        Timber.tag(TAG).d("Delete called for item: ${item.title}")

        _operationState.value = ItemOperationState.Loading

        // ✅ CRITICAL FIX: Launch on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // ✅ Database check on IO thread
                if (!db.isOpen) {
                    throw IllegalStateException("Database not available")
                }

                repository.delete(item)
                Timber.tag(TAG).i("✓ Item deleted successfully: ${item.title}")

                // ✅ Audit logging on IO thread
                val user = userDao.getUser(item.userId)
                auditLogger.logDataAccess(
                    userId = item.userId,
                    username = user?.username ?: "Unknown",
                    action = "Deleted password item: ${item.title}",
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                // ✅ Update UI state on Main thread
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Success
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to delete item")

                // ✅ Try to log audit failure (still on IO)
                try {
                    if (db.isOpen) {
                        val user = userDao.getUser(item.userId)
                        auditLogger.logDataAccess(
                            userId = item.userId,
                            username = user?.username ?: "Unknown",
                            action = "Failed to delete password item: ${item.title}",
                            resourceType = "ITEM",
                            resourceId = item.id.toString(),
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = e.localizedMessage
                        )
                    }
                } catch (logError: Exception) {
                    Timber.tag(TAG).w(logError, "Could not log audit")
                }

                // ✅ Update UI state on Main thread
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Error(
                        "Failed to delete item: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    /**
     * Get decrypted password for an item
     * ✅ FIXED: Decryption on IO dispatcher
     */
    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun getDecryptedPassword(item: Item): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val decrypted = cryptoManager.decrypt(item.encryptedPassword)

            // ✅ Audit logging on IO thread
            val user = userDao.getUser(_userId.value)
            auditLogger.logDataAccess(
                userId = _userId.value,
                username = user?.username ?: "Unknown",
                action = "Accessed password for: ${item.title}",
                resourceType = "ITEM",
                resourceId = item.id.toString(),
                outcome = AuditOutcome.SUCCESS,
                securityLevel = "ELEVATED"
            )

            decrypted
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to decrypt password for: ${item.title}")

            // ✅ Try to log audit failure (still on IO)
            try {
                val user = userDao.getUser(_userId.value)
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username ?: "Unknown",
                    action = "Failed to decrypt password for: ${item.title}",
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.localizedMessage,
                    securityLevel = "ELEVATED"
                )
            } catch (logError: Exception) {
                Timber.tag(TAG).w(logError, "Could not log audit")
            }

            withContext(Dispatchers.Main) {
                _operationState.value = ItemOperationState.Error(
                    "Failed to decrypt password: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.tag(TAG).d("ItemViewModel cleared")
    }
}
