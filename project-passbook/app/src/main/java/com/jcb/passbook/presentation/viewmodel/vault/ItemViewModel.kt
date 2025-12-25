package com.jcb.passbook.presentation.viewmodel.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.model.Item
import com.jcb.passbook.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private companion object {
        const val TAG = "ItemViewModel"
    }

    // State flows for UI
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedItem = MutableStateFlow<Item?>(null)
    val selectedItem: StateFlow<Item?> = _selectedItem.asStateFlow()

    // Thread-safe operation flag
    // Fixes: P1 - Non-thread-safe state (was using regular Boolean)
    private val isOperationInProgress = AtomicBoolean(false)

    private var currentJob: Job? = null

    init {
        loadItems()
    }

    /**
     * Load all items from repository
     */
    fun loadItems() {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "📦 Loading items...")

                // Use Result wrapper pattern
                val result = repository.getAllItems()

                when {
                    result.isSuccess -> {
                        val itemList = result.getOrNull() ?: emptyList()
                        _items.value = itemList
                        Log.i(TAG, "✅ Loaded ${itemList.size} items")
                    }
                    result.isFailure -> {
                        val exception = result.exceptionOrNull()
                        val errorMessage = exception?.message ?: "Failed to load items"
                        _error.value = errorMessage
                        Log.e(TAG, "❌ Error loading items: $errorMessage", exception)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "❌ Unexpected error in loadItems: ${e.message}", e)
            } finally {
                _isLoading.value = false
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Get item by ID
     * Fixes: P0 - Flow type error (missing .first())
     */
    fun getItemById(id: Int) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "🔍 Getting item with id: $id")

                // CRITICAL FIX: Use .first() to convert Flow to Item
                // Fixes: P0 - Flow type error (was missing .first())
                val item = repository.getItemById(id).first()

                _selectedItem.value = item
                Log.i(TAG, "✅ Item loaded: ${item?.title ?: "Unknown"}")

            } catch (e: Exception) {
                _error.value = "Failed to get item: ${e.message}"
                Log.e(TAG, "❌ Error getting item: ${e.message}", e)
            } finally {
                _isLoading.value = false
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Create new item
     */
    fun createItem(item: Item) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "➕ Creating item: ${item.title}")

                val result = repository.createItem(item)

                when {
                    result.isSuccess -> {
                        Log.i(TAG, "✅ Item created: ${item.title}")
                        loadItems() // Refresh list
                    }
                    result.isFailure -> {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to create item"
                        _error.value = errorMessage
                        Log.e(TAG, "❌ Error creating item: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "❌ Unexpected error in createItem: ${e.message}", e)
            } finally {
                _isLoading.value = false
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Update existing item
     * Fixes: P0 - Flow type error (missing .first())
     * Fixes: P1 - Race conditions
     */
    fun updateItem(item: Item) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "✏️ Updating item: ${item.title}")

                // Get existing item for comparison
                // CRITICAL FIX: Use .first() to convert Flow to Item
                // Fixes: P0 - This was crashing when editing items!
                val existingItem = repository.getItemById(item.id).first()

                if (existingItem == null) {
                    _error.value = "Item not found"
                    Log.w(TAG, "⚠️ Item ${item.id} not found")
                    return@launch
                }

                val result = repository.updateItem(item)

                when {
                    result.isSuccess -> {
                        Log.i(TAG, "✅ Item updated: ${item.title}")
                        loadItems() // Refresh list
                    }
                    result.isFailure -> {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to update item"
                        _error.value = errorMessage
                        Log.e(TAG, "❌ Error updating item: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "❌ Unexpected error in updateItem: ${e.message}", e)
            } finally {
                _isLoading.value = false
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Delete item
     */
    fun deleteItem(id: Int) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "🗑️ Deleting item with id: $id")

                val result = repository.deleteItem(id)

                when {
                    result.isSuccess -> {
                        Log.i(TAG, "✅ Item deleted")
                        loadItems() // Refresh list
                    }
                    result.isFailure -> {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete item"
                        _error.value = errorMessage
                        Log.e(TAG, "❌ Error deleting item: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "❌ Unexpected error in deleteItem: ${e.message}", e)
            } finally {
                _isLoading.value = false
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Clear all vault items (logout)
     * Fixes: P1 - Missing clearVault() method (crashes on logout!)
     */
    fun clearVault() {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                Log.d(TAG, "🧹 Clearing vault...")

                val result = repository.clearVault()

                when {
                    result.isSuccess -> {
                        _items.value = emptyList()
                        _selectedItem.value = null
                        Log.i(TAG, "✅ Vault cleared")
                    }
                    result.isFailure -> {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to clear vault"
                        _error.value = errorMessage
                        Log.e(TAG, "❌ Error clearing vault: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "❌ Unexpected error in clearVault: ${e.message}", e)
            } finally {
                _isLoading.value = false
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Clear selected item
     */
    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    /**
     * Cleanup on ViewModel destroy
     */
    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        Log.d(TAG, "✅ ItemViewModel cleared")
    }
}
