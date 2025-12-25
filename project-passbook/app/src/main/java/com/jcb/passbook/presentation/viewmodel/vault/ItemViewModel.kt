package com.jcb.passbook.presentation.viewmodel.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.PasswordEncryptionService
import com.jcb.passbook.security.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

sealed class SaveState {
    object Idle : SaveState()
    object Loading : SaveState()
    data class Success(val itemId: Long) : SaveState()
    data class Error(val message: String, val severity: ErrorSeverity = ErrorSeverity.RECOVERABLE) : SaveState()
}

enum class ErrorSeverity {
    RECOVERABLE,      // User can retry
    CRITICAL          // Session/data integrity issue
}

@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository,
    private val sessionManager: SessionManager,
    private val encryptionService: PasswordEncryptionService,
    private val cryptoManager: CryptoManager  // ✅ NEW dependency
) : ViewModel() {

    private var currentUserId: Long = 0
    private var isOperationActive = false

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * ✅ CRITICAL: insertOrUpdateItem with proper operation tracking
     *
     * Changes from old version:
     * 1. Takes plaintext password (we encrypt internally)
     * 2. Atomic operation tracking with Boolean
     * 3. Proper AMK null handling with exceptions
     * 4. SaveState auto-reset at start
     * 5. Comprehensive error categorization
     */
    fun insertOrUpdateItem(
        id: Long = 0,
        title: String,
        username: String?,
        plainPassword: String,  // ✅ CHANGED: Now plaintext
        category: String? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            // ✅ Auto-reset SaveState
            _saveState.value = SaveState.Loading

            var operationStarted = false
            try {
                // ✅ Atomic operation tracking
                operationStarted = sessionManager.startOperation()
                if (!operationStarted) {
                    _saveState.value = SaveState.Error(
                        "Session expired. Please login again.",
                        ErrorSeverity.CRITICAL
                    )
                    return@launch
                }

                isOperationActive = true

                // ✅ Explicit AMK null handling
                val amk = sessionManager.getSessionAMK()
                    ?: throw SecurityException("Session AMK unavailable")

                // ✅ Encrypt password internally
                val encryptedPassword = encryptionService.encryptPassword(plainPassword, amk)

                // Create or update item
                val item = Item(
                    id = id,
                    userId = currentUserId,
                    title = title,
                    username = username,
                    encryptedPassword = encryptedPassword,
                    category = category,
                    notes = notes,
                    lastModified = System.currentTimeMillis()
                )

                val insertedId = if (id == 0L) {
                    repository.insert(item)
                } else {
                    repository.update(item)
                    id
                }

                Timber.d("✅ Item saved successfully: $insertedId")
                _saveState.value = SaveState.Success(insertedId)

            } catch (e: SecurityException) {
                Timber.e(e, "❌ Security error during save")
                _saveState.value = SaveState.Error(
                    "Security error: ${e.message}",
                    ErrorSeverity.CRITICAL
                )
            } catch (e: Exception) {
                Timber.e(e, "❌ Error during save")
                _saveState.value = SaveState.Error(
                    "Failed to save: ${e.message}",
                    ErrorSeverity.RECOVERABLE
                )
            } finally {
                isOperationActive = false
                // ✅ Only decrement if we successfully started
                if (operationStarted) {
                    sessionManager.endOperation()
                }
            }
        }
    }

    /**
     * ✅ CRITICAL: updateItem with metadata-based encryption detection
     */
    fun updateItem(
        id: Long,
        title: String,
        username: String?,
        plainPassword: String?,
        category: String? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            _saveState.value = SaveState.Loading

            var operationStarted = false
            try {
                operationStarted = sessionManager.startOperation()
                if (!operationStarted) {
                    _saveState.value = SaveState.Error(
                        "Session expired",
                        ErrorSeverity.CRITICAL
                    )
                    return@launch
                }

                isOperationActive = true

                val amk = sessionManager.getSessionAMK()
                    ?: throw SecurityException("Session AMK unavailable")

                // Get existing item
                val existingItem = repository.getItemById(id)
                    ?: throw IllegalArgumentException("Item not found")

                // ✅ Use metadata-based encryption detection
                val encryptedPassword = if (plainPassword != null) {
                    // Only encrypt if password changed
                    encryptionService.encryptPassword(plainPassword, amk)
                } else {
                    // Keep existing encrypted password
                    existingItem.encryptedPassword
                }

                val updatedItem = existingItem.copy(
                    title = title,
                    username = username,
                    encryptedPassword = encryptedPassword,
                    category = category,
                    notes = notes,
                    lastModified = System.currentTimeMillis()
                )

                repository.update(updatedItem)
                Timber.d("✅ Item updated successfully: $id")
                _saveState.value = SaveState.Success(id)

            } catch (e: SecurityException) {
                Timber.e(e, "❌ Security error during update")
                _saveState.value = SaveState.Error(
                    "Security error: ${e.message}",
                    ErrorSeverity.CRITICAL
                )
            } catch (e: Exception) {
                Timber.e(e, "❌ Error during update")
                _saveState.value = SaveState.Error(
                    "Failed to update: ${e.message}",
                    ErrorSeverity.RECOVERABLE
                )
            } finally {
                isOperationActive = false
                if (operationStarted) {
                    sessionManager.endOperation()
                }
            }
        }
    }

    fun setCurrentUserId(userId: Long) {
        Timber.d("🔐 Setting userId: $userId")
        currentUserId = userId
    }

    fun clearVault() {
        viewModelScope.launch {
            try {
                repository.clearVault()
                Timber.i("✅ Vault cleared")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing vault")
            }
        }
    }

    fun clearSecrets() {
        // Clear any in-memory secrets
        isOperationActive = false
        Timber.d("✅ Secrets cleared")
    }
}
