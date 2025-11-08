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

/**
 * FIXED: All logDataAccess() calls now use correct signature
 */

sealed class ItemOperationState {
    object Idle : ItemOperationState()
    object Loading : ItemOperationState()
    object Success : ItemOperationState()
    data class Error(val message: String) : ItemOperationState()
}

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
     * FIXED: Insert item with correct audit logging signature
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
                    name = itemName,
                    encryptedPasswordData = encryptedData,
                    userId = currentUserId
                )
                repository.insert(newItem)

                // FIXED: Audit logging with correct signature
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
                // FIXED: Audit the failure with correct signature
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
     * FIXED: Update item with correct audit logging signature
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun update(item: Item, newName: String?, newPlainTextPassword: String?) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val updatedName = newName ?: item.name
                val updatedData = newPlainTextPassword?.let {
                    cryptoManager.encrypt(it)
                } ?: item.encryptedPasswordData

                val needUpdate = updatedName != item.name ||
                        !updatedData.contentEquals(item.encryptedPasswordData)

                if (needUpdate) {
                    repository.update(item.copy(
                        name = updatedName,
                        encryptedPasswordData = updatedData
                    ))

                    // FIXED: Audit logging with correct signature
                    val user = userRepository.getUser(item.userId).first()
                    val actionDetails = buildString {
                        append("Updated password item: ${item.name}")
                        if (newName != null) append(" (renamed to: $updatedName)")
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
                // FIXED: Audit the failure with correct signature
                viewModelScope.launch {
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username ?: "Unknown",
                        action = "Failed to update password item: ${item.name}",
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
     * FIXED: Delete item with correct audit logging signature
     */
    fun delete(item: Item) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                repository.delete(item)

                // FIXED: Audit logging with correct signature
                val user = userRepository.getUser(item.userId).first()
                auditLogger.logDataAccess(
                    userId = item.userId,
                    username = user?.username ?: "Unknown",
                    action = "Deleted password item: ${item.name}",
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                // FIXED: Audit the failure with correct signature
                viewModelScope.launch {
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username ?: "Unknown",
                        action = "Failed to delete password item: ${item.name}",
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
     * FIXED: Get decrypted password with correct audit logging signature
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            val decrypted = cryptoManager.decrypt(item.encryptedPasswordData)

            // FIXED: Audit the password access with correct signature
            viewModelScope.launch {
                val user = userRepository.getUser(_userId.value).first()
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username ?: "Unknown",
                    action = "Accessed password for: ${item.name}",
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    outcome = AuditOutcome.SUCCESS,
                    securityLevel = "ELEVATED"
                )
            }

            decrypted
        } catch (e: Exception) {
            // FIXED: Audit the failure with correct signature
            viewModelScope.launch {
                val user = userRepository.getUser(_userId.value).first()
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username ?: "Unknown",
                    action = "Failed to decrypt password for: ${item.name}",
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
