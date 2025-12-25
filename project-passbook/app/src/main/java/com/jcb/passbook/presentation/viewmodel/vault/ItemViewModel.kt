package com.jcb.passbook.presentation.viewmodel.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategoryEnum
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.security.session.SessionManager
import com.jcb.passbook.security.crypto.PasswordEncryptionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for vault management
 */
data class ItemUiState(
    val items: List<Item> = emptyList(),
    val selectedCategory: PasswordCategoryEnum? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val lastSaveSuccessful: Boolean? = null
)

/**
 * SaveState - Tracks save operation status
 */
sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    data class Success(val itemId: Long) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * ItemViewModel - Manages password vault with SessionManager integration
 * ✅ FIXED: Added SessionManager and PasswordEncryptionService
 */
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val sessionManager: SessionManager, // ✅ NEW: Session management
    private val encryptionService: PasswordEncryptionService // ✅ NEW: Encryption service
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<Long?>(null)
    val currentUserId: StateFlow<Long?> = _currentUserId.asStateFlow()

    private val _uiState = MutableStateFlow(ItemUiState())
    val uiState: StateFlow<ItemUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * Set authenticated user ID
     */
    fun setCurrentUserId(userId: Long) {
        Timber.d("Setting userId: $userId")
        _currentUserId.value = userId
        loadItems()
    }

    /**
     * Load items with reactive filters
     */
    fun loadItems() {
        val userId = _currentUserId.value
        if (userId == null) {
            Timber.w("loadItems() skipped - userId not set")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                _uiState.map { it.selectedCategory },
                _uiState.map { it.searchQuery }
            ) { category, query ->
                Pair(category, query)
            }.flatMapLatest { (category, query) ->
                when {
                    query.isNotBlank() -> {
                        repository.searchItems(userId, query, category?.name)
                    }
                    category != null -> {
                        repository.getItemsByCategory(userId, category.name)
                    }
                    else -> {
                        repository.getItemsForUser(userId)
                    }
                }
            }.catch { e ->
                Timber.e(e, "Error loading items")
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }.collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false, error = null) }
            }
        }
    }

    /**
     * Filter by category
     */
    fun filterByCategory(category: PasswordCategoryEnum?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * ✅ FIXED: Insert or update with SessionManager integration
     *
     * Critical changes:
     * 1. Start operation tracking to extend session timeout
     * 2. Get AMK from SessionManager with grace period
     * 3. Encrypt password using AMK
     * 4. Always end operation tracking in finally block
     */
    fun insertOrUpdateItem(
        id: Long = 0,
        title: String,
        username: String?,
        encryptedPassword: ByteArray,
        url: String?,
        notes: String?,
        passwordCategory: PasswordCategoryEnum,
        isFavorite: Boolean = false
    ) {
        val userId = _currentUserId.value
        if (userId == null) {
            _saveState.value = SaveState.Error("User session expired")
            return
        }

        if (_saveState.value is SaveState.Loading) {
            Timber.w("Save already in progress")
            return
        }

        if (title.isBlank()) {
            _saveState.value = SaveState.Error("Title is required")
            return
        }

        if (encryptedPassword.isEmpty()) {
            _saveState.value = SaveState.Error("Password is required")
            return
        }

        viewModelScope.launch {
            try {
                // ✅ CRITICAL: Start operation - extends session timeout
                sessionManager.startOperation()

                _saveState.value = SaveState.Loading
                _uiState.update { it.copy(isSaving = true) }

                // ✅ CRITICAL: Get AMK with extended timeout
                val amk = sessionManager.getSessionAMK()
                if (amk == null) {
                    _saveState.value = SaveState.Error("User session expired")
                    _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = false, error = "Session expired") }
                    Timber.e("❌ Session expired during save")
                    return@launch
                }

                val result: Long = withContext(Dispatchers.IO) {
                    // ✅ Encrypt password with AMK
                    val encryptedPasswordData = encryptionService.encryptPassword(
                        password = String(encryptedPassword, Charsets.UTF_8),
                        amk = amk
                    )

                    val item = Item(
                        id = id,
                        userId = userId,
                        title = title.trim(),
                        username = username?.trim(),
                        encryptedPassword = encryptedPasswordData,
                        url = url?.trim(),
                        notes = notes?.trim(),
                        passwordCategory = passwordCategory.name,
                        isFavorite = isFavorite,
                        updatedAt = System.currentTimeMillis()
                    )

                    if (id == 0L) {
                        repository.insertItem(item)
                    } else {
                        repository.updateItem(item)
                        id // Return the existing ID
                    }
                }

                _saveState.value = SaveState.Success(result)
                _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = true, error = null) }
                Timber.i("✅ Password saved successfully: $title")

            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is android.database.sqlite.SQLiteConstraintException -> "Duplicate entry"
                    is android.database.sqlite.SQLiteFullException -> "Database full"
                    else -> "Failed to save: ${e.message}"
                }

                _saveState.value = SaveState.Error(errorMessage)
                _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = false, error = errorMessage) }
                Timber.e(e, "❌ Failed to save password")
            } finally {
                // ✅ CRITICAL: Always end operation tracking
                sessionManager.endOperation()
            }
        }
    }

    /**
     * ✅ FIXED: Update item with SessionManager integration
     */
    fun updateItem(item: Item) {
        viewModelScope.launch {
            try {
                // ✅ CRITICAL: Start operation tracking
                sessionManager.startOperation()

                _saveState.value = SaveState.Loading
                _uiState.update { it.copy(isSaving = true) }

                // ✅ CRITICAL: Get AMK with extended timeout
                val amk = sessionManager.getSessionAMK()
                if (amk == null) {
                    _saveState.value = SaveState.Error("User session expired")
                    _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = false, error = "Session expired") }
                    Timber.e("❌ Session expired during update")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    // ✅ Re-encrypt password if it's plaintext
                    val encryptedPasswordData = if (String(item.encryptedPassword, Charsets.UTF_8).startsWith("encrypted:")) {
                        // Already encrypted, keep as is
                        item.encryptedPassword
                    } else {
                        // Encrypt plaintext password
                        encryptionService.encryptPassword(
                            password = String(item.encryptedPassword, Charsets.UTF_8),
                            amk = amk
                        )
                    }

                    val updatedItem = item.copy(
                        encryptedPassword = encryptedPasswordData,
                        updatedAt = System.currentTimeMillis()
                    )

                    repository.updateItem(updatedItem)
                }

                _saveState.value = SaveState.Success(itemId = item.id)
                _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = true, error = null) }
                Timber.i("✅ Password updated successfully: ${item.title}")

            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Update failed")
                _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = false, error = e.message) }
                Timber.e(e, "❌ Failed to update password")
            } finally {
                // ✅ CRITICAL: Always end operation tracking
                sessionManager.endOperation()
            }
        }
    }

    /**
     * Delete password entry by ID
     */
    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSaving = true) }
                withContext(Dispatchers.IO) {
                    repository.deleteById(itemId)
                }
                _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = true, error = null) }
                Timber.i("✅ Password deleted: $itemId")
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, lastSaveSuccessful = false, error = e.message) }
                Timber.e(e, "❌ Failed to delete password")
            }
        }
    }

    /**
     * Reset save state
     */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
        _uiState.update { it.copy(lastSaveSuccessful = null, error = null) }
    }

    /**
     * Clear vault data
     */
    fun clearVault() {
        _currentUserId.value = null
        _uiState.update { ItemUiState() }
        _saveState.value = SaveState.Idle
    }

    fun clearSecrets() {
        _uiState.update { it.copy(items = emptyList(), error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, lastSaveSuccessful = null) }
        _saveState.value = SaveState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        clearSecrets()
    }
}
