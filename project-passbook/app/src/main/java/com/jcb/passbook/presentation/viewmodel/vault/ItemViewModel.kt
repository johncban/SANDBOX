package com.jcb.passbook.presentation.viewmodel.vault

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategoryEnum
import com.jcb.passbook.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

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
    val selectedCategory: PasswordCategoryEnum? = null
)

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val TAG = "ItemViewModel"
    }

    // ✅ FIXED: Get userId from SavedStateHandle
    private val userId: Long
        get() = savedStateHandle.get<Long>("userId") ?: 0L

    private val _uiState = MutableStateFlow(ItemUiState())
    val uiState: StateFlow<ItemUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _selectedItem = MutableStateFlow<Item?>(null)
    val selectedItem: StateFlow<Item?> = _selectedItem.asStateFlow()

    private val isOperationInProgress = AtomicBoolean(false)
    private var currentJob: Job? = null

    init {
        loadItems()
    }

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
                        var itemList = result.getOrNull() ?: emptyList()

                        val currentCategory = _uiState.value.selectedCategory
                        if (currentCategory != null) {
                            itemList = itemList.filter { item ->
                                item.getPasswordCategoryEnum() == currentCategory
                            }
                        }

                        val searchQuery = _uiState.value.searchQuery
                        if (searchQuery.isNotBlank()) {
                            itemList = itemList.filter { item ->
                                item.title.contains(searchQuery, ignoreCase = true) ||
                                        item.username?.contains(searchQuery, ignoreCase = true) == true ||
                                        item.notes?.contains(searchQuery, ignoreCase = true) == true
                            }
                        }

                        _uiState.update { it.copy(items = itemList, isLoading = false) }
                        Log.i(TAG, "✅ Loaded ${itemList.size} items (filtered)")
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
                    Log.d(TAG, "📝 Creating new item")
                    repository.createItem(item)
                } else {
                    Log.d(TAG, "✏️ Updating existing item (id=${item.id})")
                    repository.updateItem(item)
                }

                when {
                    result.isSuccess -> {
                        _saveState.value = SaveState.Success("Item saved successfully")
                        Log.i(TAG, "✅ Item saved: ${item.title}")
                        loadItems()
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
                        Log.i(TAG, "✅ Item deleted (id=$id)")
                        loadItems()
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

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadItems()
    }

    fun filterByCategory(category: PasswordCategoryEnum?) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadItems()
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        Log.d(TAG, "✅ ItemViewModel cleared")
    }
}
