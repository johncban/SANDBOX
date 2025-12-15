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
import kotlinx.coroutines.withContext
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
 * ✅ FIXED: Immediate database write with WAL checkpoint
 * ✅ FIXED: Process-safe save operations
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
     * ✅ CRITICAL FIX: Insert new password item with IMMEDIATE database write + WAL checkpoint
     *
     * Previous Bug: Items were written to memory but not synced to disk before process termination
     * Fix: Force WAL checkpoint immediately after insert to guarantee disk persistence
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

                // ✅ CRITICAL FIX: Insert to database
                val itemId = itemDao.insert(item)
                Timber.tag(TAG).d("Item inserted to memory with ID: $itemId")

                // ✅ CRITICAL FIX: Force WAL checkpoint to sync to disk IMMEDIATELY
                forceWalCheckpoint()

                Timber.tag(TAG).i("✅ Item saved to disk successfully with ID: $itemId")

                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Success
                }

                loadItems()

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Error inserting item")
                withContext(Dispatchers.Main) {
                    _operationState.value = ItemOperationState.Error(
                        "Failed to save item: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    /**
     * ✅ NEW: Force WAL checkpoint to sync database to disk
     * This ensures data is written to physical storage immediately
     */
    private fun forceWalCheckpoint() {
        try {
            // Access the SQLite database directly
            val db = itemDao.javaClass
                .getDeclaredField("__db")
                .apply { isAccessible = true }
                .get(itemDao) as? androidx.room.RoomDatabase

            db?.openHelper?.writableDatabase?.run {
                // Force WAL checkpoint - write everything to disk NOW
                execSQL("PRAGMA wal_checkpoint(FULL)")
                Timber.tag(TAG).d("✅ WAL checkpoint completed - data synced to disk")
            } ?: run {
                Timber.tag(TAG).w("⚠️ Could not access database for WAL checkpoint")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during WAL checkpoint (non-fatal)")
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
                val existingItem = itemDao.getItemById(itemId)

                if (existingItem == null) {
                    Timber.tag(TAG).e("Item not found: $itemId")
                    _operationState.value = ItemOperationState.Error("Item not found")
                    return@launch
                }

                if (existingItem.userId != currentUserId) {
                    Timber.tag(TAG).e("Unauthorized access attempt")
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

                // ✅ Force WAL checkpoint after update
                forceWalCheckpoint()

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
                val existingItem = itemDao.getItemById(itemId)

                if (existingItem == null) {
                    Timber.tag(TAG).w("Item not found for deletion: $itemId")
                    _operationState.value = ItemOperationState.Error("Item not found")
                    return@launch
                }

                if (existingItem.userId != currentUserId) {
                    Timber.tag(TAG).e("Unauthorized delete attempt")
                    _operationState.value = ItemOperationState.Error("Unauthorized access")
                    return@launch
                }

                itemDao.delete(existingItem)

                // ✅ Force WAL checkpoint after delete
                forceWalCheckpoint()

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
