package com.jcb.passbook.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.repository.ItemRepository
import com.jcb.passbook.room.Item
import com.jcb.passbook.util.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
    private val cryptoManager: CryptoManager
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

    @RequiresApi(Build.VERSION_CODES.M)
    fun getDecryptedPassword(item: Item): String? {
        return try {
            cryptoManager.decrypt(item.encryptedPasswordData)
        } catch (e: Exception) {
            _operationState.value = ItemOperationState.Error("Failed to decrypt password: ${e.localizedMessage}")
            null
        }
    }
}
