package com.jcb.passbook.presentation.viewmodel.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.repository.ItemRepository
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

/**
 * ItemViewModel - FINAL FIXED VERSION
 *
 * ALL 14 REMAINING TYPE MISMATCHES FIXED:
 * ✅ Changed ALL Item.userId from Int to Long (lines 48, 93, 109, 155, 170, 197, 211, 239, 256)
 * ✅ Changed ALL _userId.value from Int to Long
 * ✅ Changed ALL currentUserId from Int to Long
 * ✅ Fixed ALL getItemsForUser() calls to use Long
 * ✅ All type parameters are now consistent
 */

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val cryptoManager: CryptoManager,
    private val auditLogger: AuditLogger,
    private val userDao: UserDao
) : ViewModel() {

    // ✅ FIXED: Changed from Int to Long throughout entire file
    private val _userId = MutableStateFlow<Long>(-1L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    // ✅ FIXED: Explicit type parameter and Long type for getItemsForUser
    val items: StateFlow<List<Item>> = _userId
        .flatMapLatest { id ->
            if (id != -1L) {
                repository.getItemsForUser(id)  // ✅ FIXED: id is Long now
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationState = MutableStateFlow<ItemOperationState>(ItemOperationState.Idle)
    val operationState: StateFlow<ItemOperationState> = _operationState.asStateFlow()

    // ✅ FIXED: Parameter changed from Int to Long
    fun setUserId(userId: Long) {
        _userId.value = userId
    }

    fun clearAllItems() {
        _userId.value = -1L
    }

    fun clearOperationState() {
        _operationState.value = ItemOperationState.Idle
    }

    /**
     * Insert new password item with encryption and audit logging
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun insert(itemName: String, plainTextPassword: String) {
        // ✅ FIXED: currentUserId is now Long (line 48)
        val currentUserId: Long = _userId.value
        if (currentUserId == -1L) {
            _operationState.value = ItemOperationState.Error("No user ID set")
            return
        }

        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val encryptedData = cryptoManager.encrypt(plainTextPassword)
                val newItem = Item(
                    title = itemName,
                    encryptedPassword = encryptedData,
                    userId = currentUserId  // ✅ FIXED: Long type (line 93)
                )

                repository.insert(newItem)

                // ✅ FIXED: UserDao.getUser() with Long parameter (line 109)
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
                viewModelScope.launch {
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
                Timber.tag(TAG).e(e, "Failed to insert item")
                _operationState.value = ItemOperationState.Error("Failed to insert item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Update existing password item
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun update(item: Item, newName: String?, newPlainTextPassword: String?) {
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

                    // ✅ FIXED: item.userId is Long (lines 155, 170)
                    val user = userDao.getUser(item.userId)
                    val actionDetails = buildString {
                        append("Updated password item: ${item.title}")
                        if (newName != null) append(" (renamed to: $updatedTitle)")
                        if (newPlainTextPassword != null) append(" (password changed)")
                    }

                    auditLogger.logDataAccess(
                        userId = item.userId,  // ✅ FIXED: Long type
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
                    val user = userDao.getUser(item.userId)
                    auditLogger.logDataAccess(
                        userId = item.userId,  // ✅ FIXED: Long type
                        username = user?.username ?: "Unknown",
                        action = "Failed to update password item: ${item.title}",
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = e.localizedMessage
                    )
                }
                Timber.tag(TAG).e(e, "Failed to update item")
                _operationState.value = ItemOperationState.Error("Failed to update item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Delete password item
     */
    fun delete(item: Item) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                repository.delete(item)

                // ✅ FIXED: item.userId is Long (lines 197, 211)
                val user = userDao.getUser(item.userId)
                auditLogger.logDataAccess(
                    userId = item.userId,  // ✅ FIXED: Long type
                    username = user?.username ?: "Unknown",
                    action = "Deleted password item: ${item.title}",
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                viewModelScope.launch {
                    val user = userDao.getUser(item.userId)
                    auditLogger.logDataAccess(
                        userId = item.userId,  // ✅ FIXED: Long type
                        username = user?.username ?: "Unknown",
                        action = "Failed to delete password item: ${item.title}",
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = e.localizedMessage
                    )
                }
                Timber.tag(TAG).e(e, "Failed to delete item")
                _operationState.value = ItemOperationState.Error("Failed to delete item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Get decrypted password for viewing/copying
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            val decrypted = cryptoManager.decrypt(item.encryptedPassword)

            viewModelScope.launch {
                // ✅ FIXED: _userId.value is Long (lines 239, 256)
                val user = userDao.getUser(_userId.value)
                auditLogger.logDataAccess(
                    userId = _userId.value,  // ✅ FIXED: Long type
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
                    userId = _userId.value,  // ✅ FIXED: Long type
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
