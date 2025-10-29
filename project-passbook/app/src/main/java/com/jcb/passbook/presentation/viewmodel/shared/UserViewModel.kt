package com.jcb.passbook.presentation.viewmodel.shared

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.session.SessionKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionKeyProvider: SessionKeyProvider,
    private val auditLogger: AuditLogger,
    private val cryptoManager: CryptoManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val PREF_NAME = "user_session_prefs"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
        private const val SALT_LENGTH = 32
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_LENGTH = 32
    }

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(UserUiState())
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    data class UserUiState(
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isUserAuthenticated: Boolean = false
    )

    init {
        checkUserAuthentication()
    }

    /**
     * Check if user is authenticated based on active session
     */
    private fun checkUserAuthentication() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                val isAuthenticated = sessionKeyProvider.hasActiveSession()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isUserAuthenticated = isAuthenticated
                )

                if (isAuthenticated) {
                    loadCurrentUser()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking user authentication")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Authentication check failed: ${e.message}",
                    isUserAuthenticated = false
                )
            }
        }
    }

    /**
     * Load current user data if session is active
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                sessionKeyProvider.requireActiveSession()
                val currentUserId = getCurrentUserId()
                if (currentUserId != null) {
                    userRepository.getUser(currentUserId)
                        .catch { e ->
                            Timber.e(e, "Error loading current user")
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Failed to load user data"
                            )
                        }
                        .collect { user ->
                            _currentUser.value = user
                        }
                }
            } catch (e: SessionKeyProvider.SessionLockedException) {
                Timber.w("Session expired while loading user")
                _uiState.value = _uiState.value.copy(isUserAuthenticated = false)
            } catch (e: Exception) {
                Timber.e(e, "Error loading current user")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load user: ${e.message}"
                )
            }
        }
    }

    /**
     * Create a new user
     */
    fun createUser(username: String, masterPassword: ByteArray) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                // Generate salt for password hashing
                val salt = generateSalt()

                // Hash the password
                val passwordHash = hashPassword(masterPassword, salt)

                val user = User(
                    username = username,
                    passwordHash = passwordHash,
                    salt = salt,
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis()
                )

                val userId = userRepository.insert(user)

                // Create session after successful user creation
                sessionKeyProvider.createSession(masterPassword)

                // Store current user ID for future reference
                storeCurrentUserId(userId.toInt())

                // Log audit event
                auditLogger.logSecurityEvent(
                    eventDescription = "New user account created",
                    severity = "INFO",
                    outcome = AuditOutcome.SUCCESS
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isUserAuthenticated = true
                )

                loadCurrentUser()

            } catch (e: Exception) {
                Timber.e(e, "Error creating user")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to create user: ${e.message}"
                )

                // Log failed creation
                auditLogger.logSecurityEvent(
                    eventDescription = "Failed to create user account: ${e.message}",
                    severity = "ERROR",
                    outcome = AuditOutcome.FAILURE
                )
            } finally {
                // Clear sensitive data
                masterPassword.fill(0)
            }
        }
    }

    /**
     * Authenticate user with password
     */
    fun authenticateUser(username: String, password: ByteArray) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                val user = userRepository.getUserByUsername(username)
                if (user != null && validatePassword(password, user.passwordHash, user.salt)) {
                    // Create session
                    sessionKeyProvider.createSession(password)

                    // Store current user ID
                    storeCurrentUserId(user.id)

                    // Update last login
                    val updatedUser = user.copy(lastLogin = System.currentTimeMillis())
                    userRepository.update(updatedUser)

                    // Log successful login
                    auditLogger.logSecurityEvent(
                        eventDescription = "User successfully authenticated: $username",
                        severity = "INFO",
                        outcome = AuditOutcome.SUCCESS
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isUserAuthenticated = true
                    )

                    _currentUser.value = updatedUser

                } else {
                    // Log failed login attempt
                    auditLogger.logSecurityEvent(
                        eventDescription = "Failed login attempt for user: $username",
                        severity = "WARNING",
                        outcome = AuditOutcome.FAILURE
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Invalid credentials"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error authenticating user")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Authentication failed: ${e.message}"
                )

                // Log authentication error
                auditLogger.logSecurityEvent(
                    eventDescription = "Authentication error for user $username: ${e.message}",
                    severity = "ERROR",
                    outcome = AuditOutcome.FAILURE
                )
            } finally {
                // Clear sensitive data
                password.fill(0)
            }
        }
    }

    /**
     * Logout current user
     */
    fun logout() {
        viewModelScope.launch {
            try {
                val currentUserId = getCurrentUserId()
                val currentUsername = _currentUser.value?.username

                // Clear session
                sessionKeyProvider.clearSession()

                // Clear current user data
                _currentUser.value = null
                clearCurrentUserId()

                _uiState.value = _uiState.value.copy(isUserAuthenticated = false)

                // Log logout
                auditLogger.logSecurityEvent(
                    eventDescription = "User logged out: ${currentUsername ?: "unknown"}",
                    severity = "INFO",
                    outcome = AuditOutcome.SUCCESS
                )

                Timber.d("User logged out successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error during logout")
            }
        }
    }

    /**
     * Update user profile
     */
    fun updateUser(updatedUser: User) {
        viewModelScope.launch {
            try {
                sessionKeyProvider.requireActiveSession()
                userRepository.update(updatedUser)
                _currentUser.value = updatedUser

                // Log user update
                auditLogger.logSecurityEvent(
                    eventDescription = "User profile updated: ${updatedUser.username}",
                    severity = "INFO",
                    outcome = AuditOutcome.SUCCESS
                )

            } catch (e: SessionKeyProvider.SessionLockedException) {
                _uiState.value = _uiState.value.copy(
                    isUserAuthenticated = false,
                    errorMessage = "Session expired"
                )
            } catch (e: Exception) {
                Timber.e(e, "Error updating user")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update user: ${e.message}"
                )

                // Log failed update
                auditLogger.logSecurityEvent(
                    eventDescription = "Failed to update user ${updatedUser.username}: ${e.message}",
                    severity = "ERROR",
                    outcome = AuditOutcome.FAILURE
                )
            }
        }
    }

    // Helper methods

    private fun getCurrentUserId(): Int? {
        val userId = sharedPrefs.getInt(KEY_CURRENT_USER_ID, -1)
        return if (userId != -1) userId else null
    }

    private fun storeCurrentUserId(userId: Int) {
        sharedPrefs.edit()
            .putInt(KEY_CURRENT_USER_ID, userId)
            .apply()
    }

    private fun clearCurrentUserId() {
        sharedPrefs.edit()
            .remove(KEY_CURRENT_USER_ID)
            .apply()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPassword(password: ByteArray, salt: ByteArray): String {
        return try {
            val spec = PBEKeySpec(
                password.toString(Charsets.UTF_8).toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH * 8
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val key = factory.generateSecret(spec)

            // Convert to hex string for storage
            key.encoded.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error hashing password")
            throw IllegalStateException("Password hashing failed", e)
        }
    }

    private fun validatePassword(password: ByteArray, storedHash: String, salt: ByteArray): Boolean {
        return try {
            val computedHash = hashPassword(password, salt)
            computedHash == storedHash
        } catch (e: Exception) {
            Timber.e(e, "Error validating password")
            false
        }
    }
}