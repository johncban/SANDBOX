package com.jcb.passbook.presentation.viewmodel.vault


import android.util.Log
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

/**
 * UI States for Item screens
 */
sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    data class Success(val message: String) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * Item UI State - Manages all UI-related state for item screens
 *
 * FIXED:
 * - selectedCategory is now PasswordCategoryEnum? (not String?)
 * - Type-safe category filtering with enum
 * - Proper filtering logic on repository
 * - Consistent with ItemListScreen's CategoryFilterRow
 */
data class ItemUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: PasswordCategoryEnum? = null  // ✅ FIXED: Changed from String? to PasswordCategoryEnum?
)

/**
 * ViewModel for Item/Vault operations
 * Manages UI state and coordinates with repository
 *
 * IMPROVEMENTS:
 * - Type-safe category filtering with PasswordCategoryEnum
 * - Proper filtering logic that respects Room queries
 * - insertOrUpdateItem now accepts complete Item object
 * - Better state management with atomic operations
 * - Comprehensive error handling
 * - Thread-safe operations with AtomicBoolean
 */
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private companion object {
        const val TAG = "ItemViewModel"
    }

    // ============================================================================
    // STATE FLOWS
    // ============================================================================

    // UI State - holds all display state
    private val _uiState = MutableStateFlow(ItemUiState())
    val uiState: StateFlow<ItemUiState> = _uiState.asStateFlow()

    // Save State - tracks insertion/update/deletion operations
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // Selected item for details screen
    private val _selectedItem = MutableStateFlow<Item?>(null)
    val selectedItem: StateFlow<Item?> = _selectedItem.asStateFlow()

    // Thread-safe operation flag
    private val isOperationInProgress = AtomicBoolean(false)
    private var currentJob: Job? = null

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    init {
        loadItems()
    }

    // ============================================================================
    // LOAD OPERATIONS
    // ============================================================================

    /**
     * Load all items from repository with proper filtering
     *
     * Respects current search query and category filter
     * Applies filtering locally on fetched items for safety with Room
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
                        var itemList = result.getOrNull() ?: emptyList()

                        // ✅ FIXED: Apply category filter with PasswordCategoryEnum
                        val currentCategory = _uiState.value.selectedCategory
                        if (currentCategory != null) {
                            itemList = itemList.filter { item ->
                                item.getPasswordCategoryEnum() == currentCategory
                            }
                        }

                        // ✅ Apply search filter
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

    /**
     * Get item by ID from repository
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

    // ============================================================================
    // INSERT / UPDATE OPERATIONS
    // ============================================================================

    /**
     * Insert or update item with complete Item object
     *
     * FIXED: Now accepts Item object directly instead of individual parameters
     * This ensures Room schema consistency and proper encryption handling
     *
     * @param item Complete Item to save (id=0L for new items, id>0L for updates)
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
                        loadItems() // Refresh list after save
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
     * Convenience method: Insert or update with individual parameters
     *
     * FIXED: Constructs Item object and delegates to insertOrUpdateItem(Item)
     * Maintains backward compatibility while ensuring proper Room usage
     */
    fun insertOrUpdateItem(
        id: Long = 0L,
        title: String,
        username: String?,
        encryptedPassword: ByteArray,
        url: String?,
        notes: String?,
        passwordCategory: PasswordCategoryEnum,
        isFavorite: Boolean = false
    ) {
        val item = Item(
            id = id,
            title = title,
            username = username,
            encryptedPassword = encryptedPassword,
            url = url,
            notes = notes,
            passwordCategory = passwordCategory.name, // Store as string in Room
            isFavorite = isFavorite
        )
        insertOrUpdateItem(item)
    }

    // ============================================================================
    // DELETE OPERATIONS
    // ============================================================================

    /**
     * Delete item by ID
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
                        Log.i(TAG, "✅ Item deleted (id=$id)")
                        loadItems() // Refresh list after deletion
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

    // ============================================================================
    // FILTER / SEARCH OPERATIONS
    // ============================================================================

    /**
     * Update search query and reload filtered items
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadItems() // Reload with new search filter
    }

    /**
     * Filter by category using PasswordCategoryEnum
     *
     * FIXED: Changed from String? to PasswordCategoryEnum? for type safety
     * Automatically reloads items with category filter applied
     */
    fun filterByCategory(category: PasswordCategoryEnum?) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadItems() // Reload with new category filter
    }

    // ============================================================================
    // STATE MANAGEMENT
    // ============================================================================

    /**
     * Reset save state to Idle
     */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    /**
     * Clear error message from UI state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear all vault data on logout
     */
    fun clearVault() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🧹 Clearing vault...")
                repository.clearVault()
                _uiState.update { it.copy(items = emptyList(), selectedCategory = null, searchQuery = "") }
                _selectedItem.value = null
                _saveState.value = SaveState.Idle
                Log.i(TAG, "✅ Vault cleared")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error clearing vault: ${e.message}", e)
            }
        }
    }

    /**
     * Cleanup when ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        Log.d(TAG, "✅ ItemViewModel cleared")
    }
}
