package com.jcb.passbook.presentation.viewmodel.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ItemViewModel"

/**
 * Sealed interface for Item operation states
 */
sealed interface ItemOperationState {
    object Idle : ItemOperationState
    object Loading : ItemOperationState
    object Success : ItemOperationState
    data class Error(val message: String) : ItemOperationState
}

/**
 * ItemViewModel - Manages password items operations
 */
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val itemDao: ItemDao
) : ViewModel() {

    private val _userId = MutableStateFlow(-1L)
    val userId = _userId.asStateFlow()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items = _items.asStateFlow()

    private val _operationState = MutableStateFlow<ItemOperationState>(ItemOperationState.Idle)
    val operationState = _operationState.asStateFlow()

    init {
        Timber.tag(TAG).d("ItemViewModel initialized")
    }

    /**
     * Set user ID - must be called before any insert operation
     */
    fun setUserId(userId: Long) {
        if (userId <= 0) {
            Timber.tag(TAG).e("Invalid userId: $userId")
            _operationState.value = ItemOperationState.Error("Invalid user ID")
            return
        }
        _userId.value = userId
        Timber.tag(TAG).i("✓ setUserId: $userId")
        loadItems()
    }

    /**
     * Load items for current user
     */
    private fun loadItems() {
        if (_userId.value <= 0) {
            Timber.tag(TAG).e("Cannot load items - userId not set")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                itemRepository.getItemsForUser(_userId.value).collect { itemList ->
                    _items.value = itemList
                    Timber.tag(TAG).d("✓ Loaded ${itemList.size} items")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error loading items")
                _operationState.value = ItemOperationState.Error("Failed to load items: ${e.message}")
            }
        }
    }

    /**
     * Insert new password item
     */
    fun insert(
        itemName: String,
        plainTextPassword: String,
        username: String? = null,
        url: String? = null,
        notes: String? = null
    ) {
        Timber.tag(TAG).d("insert() called: itemName=$itemName")

        val currentUserId: Long = _userId.value
        if (currentUserId <= 0) {
            val errorMsg = "No user ID set. Please logout and login again."
            Timber.tag(TAG).e("❌ $errorMsg")
            _operationState.value = ItemOperationState.Error(errorMsg)
            return
        }

        if (itemName.isBlank()) {
            _operationState.value = ItemOperationState.Error("Item name is required")
            return
        }

        if (plainTextPassword.isBlank()) {
            _operationState.value = ItemOperationState.Error("Password is required")
            return
        }

        _operationState.value = ItemOperationState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Inserting item for userId: $currentUserId")

                val item = Item(
                    id = 0L,
                    userId = currentUserId,
                    title = itemName,
                    username = username ?: "",
                    encryptedPassword = plainTextPassword.toByteArray(Charsets.UTF_8),
                    url = url ?: "",
                    notes = notes ?: "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val itemId = itemDao.insert(item)
                Timber.tag(TAG).i("✓ Item inserted successfully with ID: $itemId")

                _operationState.value = ItemOperationState.Success
                loadItems()

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Error inserting item")
                _operationState.value = ItemOperationState.Error(
                    "Failed to save item: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    /**
     * Update password item
     */
    fun update(
        itemId: Long,
        itemName: String,
        plainTextPassword: String,
        username: String? = null,
        url: String? = null,
        notes: String? = null
    ) {
        val currentUserId = _userId.value
        if (currentUserId <= 0) {
            _operationState.value = ItemOperationState.Error("No user ID set")
            return
        }

        _operationState.value = ItemOperationState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ✅ FIX: Use getItemById which returns Item? directly
                val existingItem = itemDao.getItemById(itemId)

                if (existingItem == null) {
                    Timber.tag(TAG).e("Item not found: $itemId")
                    _operationState.value = ItemOperationState.Error("Item not found")
                    return@launch
                }

                // ✅ FIX: Verify item belongs to current user
                if (existingItem.userId != currentUserId) {
                    Timber.tag(TAG).e("Unauthorized access attempt: item $itemId belongs to user ${existingItem.userId}, not $currentUserId")
                    _operationState.value = ItemOperationState.Error("Unauthorized access")
                    return@launch
                }

                val updatedItem = existingItem.copy(
                    title = itemName,
                    username = username ?: "",
                    encryptedPassword = plainTextPassword.toByteArray(Charsets.UTF_8),
                    url = url ?: "",
                    notes = notes ?: "",
                    updatedAt = System.currentTimeMillis()
                )

                itemDao.update(updatedItem)
                Timber.tag(TAG).i("✓ Item updated: $itemId")

                _operationState.value = ItemOperationState.Success
                loadItems()

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error updating item")
                _operationState.value = ItemOperationState.Error("Failed to update item: ${e.message}")
            }
        }
    }

    /**
     * Delete password item
     */
    fun delete(itemId: Long) {
        val currentUserId = _userId.value
        if (currentUserId <= 0) {
            _operationState.value = ItemOperationState.Error("No user ID set")
            return
        }

        _operationState.value = ItemOperationState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ✅ FIX: Use getItemById which returns Item? directly
                val existingItem = itemDao.getItemById(itemId)

                if (existingItem == null) {
                    Timber.tag(TAG).w("Item not found for deletion: $itemId")
                    _operationState.value = ItemOperationState.Error("Item not found")
                    return@launch
                }

                // ✅ FIX: Verify item belongs to current user
                if (existingItem.userId != currentUserId) {
                    Timber.tag(TAG).e("Unauthorized delete attempt: item $itemId belongs to user ${existingItem.userId}, not $currentUserId")
                    _operationState.value = ItemOperationState.Error("Unauthorized access")
                    return@launch
                }

                itemDao.delete(existingItem)
                Timber.tag(TAG).i("✓ Item deleted: $itemId")

                _operationState.value = ItemOperationState.Success
                loadItems()

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error deleting item")
                _operationState.value = ItemOperationState.Error("Failed to delete item: ${e.message}")
            }
        }
    }

    /**
     * Clear operation state
     */
    fun clearOperationState() {
        _operationState.value = ItemOperationState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        Timber.tag(TAG).d("ItemViewModel cleared")
    }
}
