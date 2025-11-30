// @/app/src/main/java/com/jcb/passbook/presentation/viewmodel/vault/ItemViewModel.kt

package com.jcb.passbook.presentation.viewmodel.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ItemViewModel"

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val cryptoManager: CryptoManager,
    private val auditLogger: AuditLogger,
    private val userDao: UserDao,
    private val db: AppDatabase
) : ViewModel() {

    private val _userId = MutableStateFlow(-1L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    val items: StateFlow<List<Item>> = _userId
        .flatMapLatest { id ->
            if (id != -1L) {
                repository.getItemsForUser(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationState = MutableStateFlow<ItemOperationState>(ItemOperationState.Idle)
    val operationState: StateFlow<ItemOperationState> = _operationState.asStateFlow()

    fun setUserId(userId: Long) {
        Timber.tag(TAG).i("setUserId called with: $userId (previous: ${_userId.value})")
        _userId.value = userId
        Timber.tag(TAG).i("userId successfully updated to: ${_userId.value}")
    }

    fun clearAllItems() {
        _userId.value = -1L
    }

    fun clearOperationState() {
        _operationState.value = ItemOperationState.Idle
    }

    /**
     * ✅ FIXED: Validate both userId AND database availability before insert
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun insert(itemName: String, plainTextPassword: String) {
        val currentUserId: Long = _userId.value
        Timber.tag(TAG).d("Insert called - itemName: $itemName, currentUserId: $currentUserId")

        // ✅ VALIDATION 1: Check userId
        if (currentUserId == -1L) {
            Timber.tag(TAG).e("Insert BLOCKED - No user ID set!")
            _operationState.value = ItemOperationState.Error("No user ID set. Please logout and login again.")
            return
        }

        // ✅ VALIDATION 2: Check database availability
        if (!db.isOpen) {
            Timber.tag(TAG).e("Insert BLOCKED - Database not available!")
            _operationState.value = ItemOperationState.Error("Database not available. Please restart the app.")
            return
        }

        Timber.tag(TAG).i("✓ Insert validation passed - proceeding for user: $currentUserId")
        _operationState.value = ItemOperationState.Loading

        viewModelScope.launch {
            runCatching {
                // ✅ Additional safety: verify database is still open
                if (!db.isOpen) {
                    throw IllegalStateException("Database closed during operation")
                }

                val encryptedData = cryptoManager.encrypt(plainTextPassword)
                val newItem = Item(
                    title = itemName,
                    encryptedPassword = encryptedData,
                    userId = currentUserId
                )

                Timber.tag(TAG).d("Inserting item into repository...")
                repository.insert(newItem)
                Timber.tag(TAG).i("✓ Item inserted successfully: $itemName")

                // Log audit event
                val user = userDao.getUser(currentUserId)
                auditLogger.logDataAccess(
                    userId = currentUserId,
                    username = user?.username ?: "Unknown",
                    action = "Created password item: $itemName",
                    resourceType = "ITEM",
                    resourceId = newItem.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to insert item: $itemName")

                // Try to log audit failure
                viewModelScope.launch {
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
                }

                _operationState.value = ItemOperationState.Error("Failed to insert item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * ✅ FIXED: Add database availability check before update
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun update(item: Item, newName: String?, newPlainTextPassword: String?) {
        // ✅ VALIDATION: Check database availability
        if (!db.isOpen) {
            Timber.tag(TAG).e("Update BLOCKED - Database not available!")
            _operationState.value = ItemOperationState.Error("Database not available. Please restart the app.")
            return
        }

        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val updatedTitle = newName ?: item.title
                val updatedData = newPlainTextPassword?.let {
                    cryptoManager.encrypt(it)
                } ?: item.encryptedPassword

                val needUpdate = updatedTitle != item.title ||
                        !updatedData.contentEquals(item.encryptedPassword)

                if (needUpdate) {
                    repository.update(item.copy(
                        title = updatedTitle,
                        encryptedPassword = updatedData
                    ))

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
                }

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                viewModelScope.launch {
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
                        Timber.tag(TAG).w(logError, "Could not log audit - database unavailable")
                    }
                }
                Timber.tag(TAG).e(e, "Failed to update item")
                _operationState.value = ItemOperationState.Error("Failed to update item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * ✅ Already has database check - kept as is
     */
    fun delete(item: Item) {
        if (!db.isOpen) {
            Timber.tag(TAG).e("Delete BLOCKED - Database not available!")
            _operationState.value = ItemOperationState.Error("Database not available. Please restart the app.")
            return
        }

        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                repository.delete(item)

                val user = userDao.getUser(item.userId)
                auditLogger.logDataAccess(
                    userId = item.userId,
                    username = user?.username ?: "Unknown",
                    action = "Deleted password item: ${item.title}",
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to delete item")

                viewModelScope.launch {
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
                        Timber.tag(TAG).w(logError, "Could not log audit - database unavailable")
                    }
                }

                _operationState.value = ItemOperationState.Error("Failed to delete item: ${e.localizedMessage}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            val decrypted = cryptoManager.decrypt(item.encryptedPassword)

            viewModelScope.launch {
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
            }

            decrypted
        } catch (e: Exception) {
            viewModelScope.launch {
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
            }
            _operationState.value = ItemOperationState.Error("Failed to decrypt password: ${e.localizedMessage}")
            null
        }
    }
}
