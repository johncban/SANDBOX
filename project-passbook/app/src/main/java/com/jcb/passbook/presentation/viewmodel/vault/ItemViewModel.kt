package com.jcb.passbook.presentation.viewmodel.vault

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.jcb.passbook.presentation.viewmodel.vault.ItemOperationState

/**
 * ItemViewModel - Manages password vault items with encryption and audit logging
 *
 * FIXES APPLIED:
 * - Changed all 'name' references to 'title' (Item entity field)
 * - Changed all 'encryptedPasswordData' to 'encryptedPassword' (Item entity field)
 * - Fixed all Item() constructor calls with correct parameter names
 * - Fixed all item.copy() calls with correct parameter names
 * - All audit logging uses correct AuditLogger signature
 */


/***
sealed class ItemOperationState {
    object Idle : ItemOperationState()
    object Loading : ItemOperationState()
    object Success : ItemOperationState()
    data class Error(val message: String) : ItemOperationState()
}
***/

private const val TAG = "ItemViewModel PassBook"

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val cryptoManager: CryptoManager,
    private val auditLogger: AuditLogger,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _userId = MutableStateFlow(-1)
    val userId: StateFlow<Int> = _userId.asStateFlow()

    val items: StateFlow<List<Item>> = _userId
        .flatMapLatest { id ->
            if (id != -1) repository.getItemsForUser(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationState = MutableStateFlow<ItemOperationState>(ItemOperationState.Idle)
    val operationState: StateFlow<ItemOperationState> = _operationState.asStateFlow()

    fun setUserId(userId: Int) {
        _userId.value = userId
    }

    fun clearAllItems() {
        _userId.value = -1
    }

    fun clearOperationState() {
        _operationState.value = ItemOperationState.Idle
    }

    /**
     * Insert new password item with encryption and audit logging
     * FIXED: Uses 'title' and 'encryptedPassword' fields
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun insert(itemName: String, plainTextPassword: String) {
        val currentUserId = _userId.value
        if (currentUserId == -1) {
            _operationState.value = ItemOperationState.Error("No user ID set")
            return
        }

        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val encryptedData = cryptoManager.encrypt(plainTextPassword)
                val newItem = Item(
                    title = itemName,  // ✅ FIXED: Changed from 'name' to 'title'
                    encryptedPassword = encryptedData,  // ✅ FIXED: Changed from 'encryptedPasswordData' to 'encryptedPassword'
                    userId = currentUserId
                )

                repository.insert(newItem)

                // Audit logging with correct signature
                val user = userRepository.getUser(currentUserId).first()
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
                // Audit the failure
                viewModelScope.launch {
                    val user = userRepository.getUser(currentUserId).first()
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
                Timber.e(e, "Failed to insert item")
                _operationState.value = ItemOperationState.Error("Failed to insert item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Update existing password item
     * FIXED: Uses 'title' and 'encryptedPassword' fields
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun update(item: Item, newName: String?, newPlainTextPassword: String?) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val updatedTitle = newName ?: item.title  // ✅ FIXED: Changed from item.name to item.title
                val updatedData = newPlainTextPassword?.let {
                    cryptoManager.encrypt(it)
                } ?: item.encryptedPassword  // ✅ FIXED: Changed from item.encryptedPasswordData to item.encryptedPassword

                val needUpdate = updatedTitle != item.title ||  // ✅ FIXED: Changed from item.name
                        !updatedData.contentEquals(item.encryptedPassword)  // ✅ FIXED: Changed from item.encryptedPasswordData

                if (needUpdate) {
                    repository.update(item.copy(
                        title = updatedTitle,  // ✅ FIXED: Changed from 'name' to 'title'
                        encryptedPassword = updatedData  // ✅ FIXED: Changed from 'encryptedPasswordData' to 'encryptedPassword'
                    ))

                    // Audit logging
                    val user = userRepository.getUser(item.userId).first()
                    val actionDetails = buildString {
                        append("Updated password item: ${item.title}")  // ✅ FIXED: Changed from item.name
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
                // Audit the failure
                viewModelScope.launch {
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username ?: "Unknown",
                        action = "Failed to update password item: ${item.title}",  // ✅ FIXED: Changed from item.name
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = e.localizedMessage
                    )
                }
                Timber.e(e, "Failed to update item")
                _operationState.value = ItemOperationState.Error("Failed to update item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Delete password item
     * FIXED: Uses 'title' field
     */
    fun delete(item: Item) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                repository.delete(item)

                // Audit logging
                val user = userRepository.getUser(item.userId).first()
                auditLogger.logDataAccess(
                    userId = item.userId,
                    username = user?.username ?: "Unknown",
                    action = "Deleted password item: ${item.title}",  // ✅ FIXED: Changed from item.name
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                // Audit the failure
                viewModelScope.launch {
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username ?: "Unknown",
                        action = "Failed to delete password item: ${item.title}",  // ✅ FIXED: Changed from item.name
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = e.localizedMessage
                    )
                }
                Timber.e(e, "Failed to delete item")
                _operationState.value = ItemOperationState.Error("Failed to delete item: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Get decrypted password for viewing/copying
     * FIXED: Uses 'encryptedPassword' and 'title' fields
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            val decrypted = cryptoManager.decrypt(item.encryptedPassword)  // ✅ FIXED: Changed from item.encryptedPasswordData

            // Audit the password access (elevated security level)
            viewModelScope.launch {
                val user = userRepository.getUser(_userId.value).first()
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username ?: "Unknown",
                    action = "Accessed password for: ${item.title}",  // ✅ FIXED: Changed from item.name
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS,
                    securityLevel = "ELEVATED"
                )
            }

            decrypted
        } catch (e: Exception) {
            // Audit the failure
            viewModelScope.launch {
                val user = userRepository.getUser(_userId.value).first()
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username ?: "Unknown",
                    action = "Failed to decrypt password for: ${item.title}",  // ✅ FIXED: Changed from item.name
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