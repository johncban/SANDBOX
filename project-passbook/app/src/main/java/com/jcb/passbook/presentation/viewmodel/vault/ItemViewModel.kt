package com.jcb.passbook.presentation.viewmodel.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * UI States for Item screens
 */
sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    data class Success(val message: String) : SaveState()
    data class Error(val message: String) : SaveState()
}

data class ItemUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null
)

/**
 * ViewModel for Item/Vault operations
 * Manages UI state and coordinates with repository
 */
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private companion object {
        const val TAG = "ItemViewModel"
    }

    // UI State
    private val _uiState = MutableStateFlow(ItemUiState())
    val uiState: StateFlow<ItemUiState> = _uiState.asStateFlow()

    // Save State
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // Selected item for details screen
    private val _selectedItem = MutableStateFlow<Item?>(null)
    val selectedItem: StateFlow<Item?> = _selectedItem.asStateFlow()

    // Thread-safe operation flag
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
                _uiState.update { it.copy(isLoading = true, error = null) }
                Log.d(TAG, "📦 Loading items...")

                val result = repository.getAllItems()

                when {
                    result.isSuccess -> {
                        val itemList = result.getOrNull() ?: emptyList()
                        _uiState.update { it.copy(items = itemList, isLoading = false) }
                        Log.i(TAG, "✅ Loaded ${itemList.size} items")
                    }
                    result.isFailure -> {
                        val exception = result.exceptionOrNull()
                        val errorMessage = exception?.message ?: "Failed to load items"
                        _uiState.update { it.copy(error = errorMessage, isLoading = false) }
                        Log.e(TAG, "❌ Error loading items: $errorMessage", exception)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unexpected error: ${e.message}", isLoading = false) }
                Log.e(TAG, "❌ Unexpected error in loadItems: ${e.message}", e)
            } finally {
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Get item by ID
     */
    fun getItemById(id: Long) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                Log.d(TAG, "🔍 Getting item with id: $id")

                val item = repository.getItemById(id).first()
                _selectedItem.value = item
                _uiState.update { it.copy(isLoading = false) }

                Log.i(TAG, "✅ Item loaded: ${item?.title ?: "Unknown"}")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to get item: ${e.message}", isLoading = false) }
                Log.e(TAG, "❌ Error getting item: ${e.message}", e)
            } finally {
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Insert or update item
     */
    fun insertOrUpdateItem(item: Item) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _saveState.value = SaveState.Loading
                Log.d(TAG, "💾 Saving item: ${item.title}")

                val result = if (item.id == 0L) {
                    repository.createItem(item)
                } else {
                    repository.updateItem(item)
                }

                when {
                    result.isSuccess -> {
                        _saveState.value = SaveState.Success("Item saved successfully")
                        Log.i(TAG, "✅ Item saved: ${item.title}")
                        loadItems() // Refresh list
                    }
                    result.isFailure -> {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to save item"
                        _saveState.value = SaveState.Error(errorMessage)
                        Log.e(TAG, "❌ Error saving item: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                _saveState.value = SaveState.Error("Unexpected error: ${e.message}")
                Log.e(TAG, "❌ Unexpected error in insertOrUpdateItem: ${e.message}", e)
            } finally {
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Delete item
     */
    fun deleteItem(id: Long) {
        if (isOperationInProgress.getAndSet(true)) {
            Log.w(TAG, "⚠️ Operation already in progress")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                Log.d(TAG, "🗑️ Deleting item with id: $id")

                val result = repository.deleteItem(id)

                when {
                    result.isSuccess -> {
                        Log.i(TAG, "✅ Item deleted")
                        loadItems() // Refresh list
                    }
                    result.isFailure -> {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete item"
                        _uiState.update { it.copy(error = errorMessage, isLoading = false) }
                        Log.e(TAG, "❌ Error deleting item: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Unexpected error: ${e.message}", isLoading = false) }
                Log.e(TAG, "❌ Unexpected error in deleteItem: ${e.message}", e)
            } finally {
                isOperationInProgress.set(false)
            }
        }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Filter by category
     */
    fun filterByCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Reset save state
     */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    /**
     * Clear vault on logout
     */
    fun clearVault() {
        viewModelScope.launch {
            try {
                repository.clearVault()
                _uiState.update { it.copy(items = emptyList()) }
                _selectedItem.value = null
                Log.i(TAG, "✅ Vault cleared")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing vault: ${e.message}", e)
            }
        }
    }

    /**
     * Cleanup
     */
    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        Log.d(TAG, "✅ ItemViewModel cleared")
    }
}
