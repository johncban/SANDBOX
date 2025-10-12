package com.jcb.passbook.ui.screens.passwords

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.domain.entities.repositories.ItemRepository
import com.jcb.passbook.domain.entities.repositories.UserRepository
import com.jcb.passbook.data.local.entities.AuditEventType
import com.jcb.passbook.data.local.entities.AuditOutcome
import com.jcb.passbook.domain.Item
import com.jcb.passbook.util.CryptoManager
import com.jcb.passbook.util.audit.AuditLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.jcb.passbook.ui.viewmodel.ItemOperationState




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


    /***    --------------------------------------  DO NOT DELETE  --------------------------------------
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
                val newItem = Item(name = itemName, encryptedPasswordData = encryptedData, userId = currentUserId)
                repository.insert(newItem)
                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                Timber.e(e, "Failed to insert item")
                _operationState.value = ItemOperationState.Error("Failed to insert item: ${e.localizedMessage}")
            }
        }
    }
       -------------------------------------------- DO NOT DELETE   ---------------------------------------  ***/


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
                val newItem = Item(name = itemName, encryptedPasswordData = encryptedData, userId = currentUserId)
                repository.insert(newItem)

                // Audit logging
                val user = userRepository.getUser(currentUserId).first()
                auditLogger.logDataAccess(
                    userId = currentUserId,
                    username = user?.username,
                    eventType = AuditEventType.CREATE,
                    resourceType = "ITEM",
                    resourceId = newItem.id.toString(),
                    action = "Created password item: $itemName"
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                // Audit the failure
                viewModelScope.launch {
                    val user = userRepository.getUser(currentUserId).first()
                    auditLogger.logDataAccess(
                        userId = currentUserId,
                        username = user?.username,
                        eventType = AuditEventType.CREATE,
                        resourceType = "ITEM",
                        resourceId = "UNKNOWN",
                        action = "Failed to create password item: $itemName",
                        outcome = AuditOutcome.FAILURE
                    )
                }
                Timber.e(e, "Failed to insert item")
                _operationState.value = ItemOperationState.Error("Failed to insert item: ${e.localizedMessage}")
            }
        }
    }


    /***    --------------------------------------  DO NOT DELETE  --------------------------------------
    @RequiresApi(Build.VERSION_CODES.M)
    fun update(item: Item, newName: String?, newPlainTextPassword: String?) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val updatedName = newName ?: item.name
                val updatedData = newPlainTextPassword?.let { cryptoManager.encrypt(it) } ?: item.encryptedPasswordData
                if (updatedName != item.name || !updatedData.contentEquals(item.encryptedPasswordData)) {
                    repository.update(item.copy(name = updatedName, encryptedPasswordData = updatedData))
                }
                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                Timber.e(e, "Failed to update item")
                _operationState.value = ItemOperationState.Error("Failed to update item: ${e.localizedMessage}")
            }
        }
    }
            --------------------------------------  DO NOT DELETE  --------------------------------------   ***/

    @RequiresApi(Build.VERSION_CODES.M)
    fun update(item: Item, newName: String?, newPlainTextPassword: String?) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                val updatedName = newName ?: item.name
                val updatedData = newPlainTextPassword?.let { cryptoManager.encrypt(it) } ?: item.encryptedPasswordData
                val needUpdate = updatedName != item.name || !updatedData.contentEquals(item.encryptedPasswordData)

                if (needUpdate) {
                    repository.update(item.copy(name = updatedName, encryptedPasswordData = updatedData))

                    // Audit logging on update
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username,
                        eventType = AuditEventType.UPDATE,
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        action = "Updated password item: ${item.name}" +
                                (if (newName != null) " (renamed to: $updatedName)" else "") +
                                (if (newPlainTextPassword != null) " (password changed)" else "")
                    )
                }

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                // Audit the failure
                viewModelScope.launch {
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username,
                        eventType = AuditEventType.UPDATE,
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        action = "Failed to update password item: ${item.name}",
                        outcome = AuditOutcome.FAILURE
                    )
                }
                Timber.e(e, "Failed to update item")
                _operationState.value = ItemOperationState.Error("Failed to update item: ${e.localizedMessage}")
            }
        }
    }


    /***    --------------------------------------  DO NOT DELETE  --------------------------------------
    fun delete(item: Item) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                repository.delete(item)
                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                Timber.e(e, "Failed to delete item")
                _operationState.value = ItemOperationState.Error("Failed to delete item: ${e.localizedMessage}")
            }
        }
    }
            --------------------------------------  DO NOT DELETE  --------------------------------------   ***/


    fun delete(item: Item) {
        _operationState.value = ItemOperationState.Loading
        viewModelScope.launch {
            runCatching {
                repository.delete(item)

                // Audit logging on delete
                val user = userRepository.getUser(item.userId).first()
                auditLogger.logDataAccess(
                    userId = item.userId,
                    username = user?.username,
                    eventType = AuditEventType.DELETE,
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    action = "Deleted password item: ${item.name}"
                )

                _operationState.value = ItemOperationState.Success
            }.onFailure { e ->
                // Audit the failure
                viewModelScope.launch {
                    val user = userRepository.getUser(item.userId).first()
                    auditLogger.logDataAccess(
                        userId = item.userId,
                        username = user?.username,
                        eventType = AuditEventType.DELETE,
                        resourceType = "ITEM",
                        resourceId = item.id.toString(),
                        action = "Failed to delete password item: ${item.name}",
                        outcome = AuditOutcome.FAILURE
                    )
                }
                Timber.e(e, "Failed to delete item")
                _operationState.value = ItemOperationState.Error("Failed to delete item: ${e.localizedMessage}")
            }
        }
    }


    /***    --------------------------------------  DO NOT DELETE  --------------------------------------
    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            cryptoManager.decrypt(item.encryptedPasswordData)
        } catch (e: Exception) {
            _operationState.value = ItemOperationState.Error("Failed to decrypt password: ${e.localizedMessage}")
            null
        }
    }
            --------------------------------------  DO NOT DELETE  --------------------------------------   ***/


    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            val decrypted = cryptoManager.decrypt(item.encryptedPasswordData)

            // Audit the password access
            viewModelScope.launch {
                val user = userRepository.getUser(_userId.value).first()
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username,
                    eventType = AuditEventType.READ,
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    action = "Accessed password for: ${item.name}"
                )
            }

            decrypted
        } catch (e: Exception) {
            viewModelScope.launch {
                val user = userRepository.getUser(_userId.value).first()
                auditLogger.logDataAccess(
                    userId = _userId.value,
                    username = user?.username,
                    eventType = AuditEventType.READ,
                    resourceType = "ITEM",
                    resourceId = item.id.toString(),
                    action = "Failed to decrypt password for: ${item.name}",
                    outcome = AuditOutcome.FAILURE
                )
            }
            _operationState.value = ItemOperationState.Error("Failed to decrypt password: ${e.localizedMessage}")
            null
        }
    }
}
