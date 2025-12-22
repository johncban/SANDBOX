package com.jcb.passbook.presentation.viewmodel.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategory
import com.jcb.passbook.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for password vault management
 */
data class ItemUiState(
    val items: List<Item> = emptyList(),
    val selectedCategory: PasswordCategory? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ItemViewModel - Manages password vault state and operations
 *
 * ✅ REFACTORED: Added updateItem() and deleteItem(itemId) methods
 *
 * Responsibilities:
 * - Load and filter vault items by category and search query
 * - Handle CRUD operations for password entries
 * - Manage userId session state from authentication flow
 * - Clear sensitive data on logout and ViewModel destruction
 *
 * Security considerations:
 * - Items only loaded after setCurrentUserId() called from MainActivity
 * - All repository queries filtered by authenticated userId
 * - Secrets cleared from memory in onCleared() and clearSecrets()
 */
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    // Current authenticated user ID - null until login succeeds
    private val _currentUserId = MutableStateFlow<Long?>(null)
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()

    // UI state exposed to composables
    private val _uiState = MutableStateFlow(ItemUiState())
    val uiState: StateFlow<ItemUiState> = _uiState.asStateFlow()

    /**
     * Set current authenticated user ID for vault queries
     * Called from MainActivity after successful login or registration
     *
     * @param userId Authenticated user ID from AuthState.Success
     */
    fun setCurrentUserId(userId: Long) {
        Timber.d("Setting current userId: $userId")
        _currentUserId.value = userId
        loadItems()
    }

    /**
     * Load items with reactive filters for category and search
     * Automatically triggered when userId, category, or search query changes
     */
    fun loadItems() {
        val userId = _currentUserId.value
        if (userId == null) {
            Timber.w("loadItems() called before userId set - skipping")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Combine category and search filters reactively
            combine(
                _uiState.map { it.selectedCategory },
                _uiState.map { it.searchQuery }
            ) { category, query ->
                Pair(category, query)
            }.flatMapLatest { (category, query) ->
                when {
                    query.isNotBlank() -> {
                        Timber.d("Searching items: userId=$userId, query=$query, category=$category")
                        repository.searchItems(userId, query, category)
                    }
                    category != null -> {
                        Timber.d("Filtering by category: userId=$userId, category=$category")
                        repository.getItemsByCategory(userId, category)
                    }
                    else -> {
                        Timber.d("Loading all items for userId=$userId")
                        repository.getItemsForUser(userId)
                    }
                }
            }.catch { e ->
                Timber.e(e, "Error loading items")
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }.collect { items ->
                Timber.d("Loaded ${items.size} items")
                _uiState.update { it.copy(items = items, isLoading = false, error = null) }
            }
        }
    }

    /**
     * Filter vault items by password category
     * Pass null to show all categories
     *
     * @param category PasswordCategory enum or null for all
     */
    fun filterByCategory(category: PasswordCategory?) {
        Timber.d("Filtering by category: $category")
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Update search query for filtering vault items
     * Searches across title, username, and notes fields
     *
     * @param query Search string (case-insensitive)
     */
    fun updateSearchQuery(query: String) {
        Timber.d("Updating search query: $query")
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Insert new or update existing password entry
     * Validates userId is set before operation
     *
     * @param id Item ID (0 for new, existing ID for update)
     * @param title Password entry title (required)
     * @param username Username or email (optional)
     * @param encryptedPassword Encrypted password bytes (required)
     * @param url Website URL (optional)
     * @param notes Description or context notes (optional)
     * @param passwordCategory PasswordCategory enum (required)
     * @param isFavorite Mark as favorite (default false)
     */
    fun insertOrUpdateItem(
        id: Long = 0,
        title: String,
        username: String?,
        encryptedPassword: ByteArray,
        url: String?,
        notes: String?,
        passwordCategory: PasswordCategory,
        isFavorite: Boolean = false
    ) {
        val userId = _currentUserId.value
        if (userId == null) {
            Timber.e("Cannot insert/update item: userId not set")
            _uiState.update { it.copy(error = "User session expired") }
            return
        }

        viewModelScope.launch {
            try {
                val item = Item(
                    id = id,
                    userId = userId,
                    title = title.trim(),
                    username = username?.trim(),
                    encryptedPassword = encryptedPassword,
                    url = url?.trim(),
                    notes = notes?.trim(),
                    passwordCategory = passwordCategory.name,
                    isFavorite = isFavorite,
                    updatedAt = System.currentTimeMillis()
                )

                if (id == 0L) {
                    val newId = repository.insertItem(item)
                    Timber.i("Inserted new item: id=$newId, title=$title")
                } else {
                    repository.updateItem(item)
                    Timber.i("Updated item: id=$id, title=$title")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error inserting/updating item")
                _uiState.update { it.copy(error = "Failed to save password: ${e.message}") }
            }
        }
    }

    /**
     * ✅ NEW: Update existing password item (for modal bottom sheet)
     *
     * @param item Item entity with updated values
     */
    fun updateItem(item: Item) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                repository.updateItem(item)
                Timber.i("Updated item: id=${item.id}, title=${item.title}")
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                Timber.e(e, "Error updating item")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to update password"
                    )
                }
            }
        }
    }

    /**
     * ✅ NEW: Delete password entry by ID (for modal bottom sheet)
     *
     * @param itemId Item ID to delete
     */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                repository.deleteById(itemId)
                Timber.i("Deleted item: id=$itemId")
                _uiState.update { it.copy(isLoading = false, error = null) }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting item")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to delete password"
                    )
                }
            }
        }
    }

    /**
     * Delete password entry from vault (legacy method - kept for compatibility)
     *
     * @param item Item entity to delete
     */
    fun deleteItem(item: Item) {
        deleteItem(item.id)
    }

    /**
     * Clear vault data and user session
     * Called on logout or session expiration from MainActivity
     * Resets userId and UI state to initial values
     */
    fun clearVault() {
        Timber.i("Clearing vault data and userId")
        _currentUserId.value = null
        _uiState.update { ItemUiState() }
    }

    /**
     * Clear sensitive password data from memory
     * Called in onCleared() and from MainActivity onDestroy
     * Keeps userId for potential re-authentication but clears item list
     */
    fun clearSecrets() {
        Timber.i("Clearing sensitive data from memory")
        _uiState.update { it.copy(items = emptyList(), error = null) }
    }

    /**
     * Clear error message from UI state
     * Typically called after user dismisses error snackbar
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * ViewModel cleanup - clear secrets on destruction
     */
    override fun onCleared() {
        super.onCleared()
        clearSecrets()
        Timber.d("ItemViewModel cleared")
    }
}
