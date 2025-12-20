package com.jcb.passbook.presentation.viewmodel.shared

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.R
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.SessionManager
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.inject.Inject

/**
 * Authentication state for login flow
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userId: Long) : AuthState()
    data class Error(@StringRes val messageId: Int, val details: String? = null) : AuthState()
}

/**
 * Registration state for user creation flow
 */
sealed class RegistrationState {
    object Idle : RegistrationState()
    object Loading : RegistrationState()
    data class Success(val userId: Long) : RegistrationState()
    data class Error(@StringRes val messageId: Int, val details: String? = null) : RegistrationState()
}

/**
 * Key rotation state for manual database key rotation
 */
sealed class KeyRotationState {
    object Idle : KeyRotationState()
    object Loading : KeyRotationState()
    object Success : KeyRotationState()
    data class Error(val message: String) : KeyRotationState()
}

/**
 * UserViewModel - Manages authentication, registration, and user session
 *
 * ✅ REFACTORED: Authentication flow separated from session management
 * - ViewModel authenticates user and emits userId
 * - UI layer (Screen composables) handle session start with SessionManager
 *
 * Responsibilities:
 * - Authenticate users with Argon2id password hashing
 * - Register new users with secure password storage
 * - Emit authentication state for UI to handle session creation
 * - Log all authentication events via AuditLogger
 * - Handle database key rotation (manual only, no automatic rotation)
 *
 * Security considerations:
 * - Passwords hashed with Argon2id (5 iterations, 64MB memory, parallelism 2)
 * - Salt generated with SecureRandom (16 bytes)
 * - Session management delegated to UI layer with FragmentActivity access
 * - All auth events audited with outcome and error details
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val argon2Kt: Argon2Kt,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _keyRotationState = MutableStateFlow<KeyRotationState>(KeyRotationState.Idle)
    val keyRotationState: StateFlow<KeyRotationState> = _keyRotationState.asStateFlow()

    /**
     * ✅ REFACTORED: Authenticate user with username and password
     * Only validates credentials - does NOT start session
     * Session is started by UI layer after observing AuthState.Success
     *
     * @param username User's username (trimmed automatically)
     * @param password User's plaintext password
     */
    fun login(username: String, password: String) {
        val trimmedUsername = username.trim()

        // Validate input
        if (trimmedUsername.isBlank() || password.isBlank()) {
            auditLogger.logAuthentication(
                username = trimmedUsername.ifBlank { "UNKNOWN" },
                eventType = AuditEventType.AUTHENTICATION_FAILURE,
                outcome = AuditOutcome.FAILURE,
                errorMessage = "Empty credentials provided"
            )
            _authState.value = AuthState.Error(R.string.error_credentials_empty)
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = userRepository.getUserByUsername(trimmedUsername)
                if (user != null && verifyPassword(password, user.passwordHash, user.salt)) {
                    // ✅ FIXED: Only emit success with userId - session start delegated to UI
                    auditLogger.logAuthentication(
                        username = user.username,
                        eventType = AuditEventType.LOGIN,
                        outcome = AuditOutcome.SUCCESS
                    )
                    Timber.i("Login successful for user: ${user.username}, userId: ${user.id}")
                    _authState.value = AuthState.Success(user.id)
                } else {
                    // Invalid credentials
                    auditLogger.logAuthentication(
                        username = trimmedUsername,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Invalid username or password"
                    )
                    Timber.w("Login failed for user: $trimmedUsername")
                    _authState.value = AuthState.Error(R.string.error_invalid_credentials)
                }
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = trimmedUsername,
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Login exception: ${e.message}"
                )
                Timber.e(e, "Login failure for user: $trimmedUsername")
                _authState.value = AuthState.Error(R.string.error_login_failed, e.localizedMessage)
            }
        }
    }

    /**
     * Register new user with username and password
     * Validates input, checks username uniqueness, hashes password, and creates user
     *
     * @param username Desired username (trimmed, must be unique)
     * @param password Plaintext password (minimum 8 characters)
     */
    fun register(username: String, password: String) {
        val trimmedUsername = username.trim()

        // Validate input
        when {
            trimmedUsername.isBlank() -> {
                _registrationState.value = RegistrationState.Error(R.string.error_empty_username)
                return
            }
            password.isBlank() -> {
                _registrationState.value = RegistrationState.Error(R.string.error_empty_password)
                return
            }
            password.length < 8 -> {
                _registrationState.value = RegistrationState.Error(R.string.error_password_length)
                return
            }
        }

        _registrationState.value = RegistrationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check username uniqueness
                if (userRepository.getUserByUsername(trimmedUsername) != null) {
                    auditLogger.logAuthentication(
                        username = trimmedUsername,
                        eventType = AuditEventType.REGISTER,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Username already exists"
                    )
                    Timber.w("Registration failed: username already exists: $trimmedUsername")
                    _registrationState.value = RegistrationState.Error(R.string.error_username_exists)
                    return@launch
                }

                // Hash password with Argon2id
                val salt = generateSalt()
                val passwordHash = hashPassword(password, salt)
                val newUser = User(
                    username = trimmedUsername,
                    passwordHash = passwordHash,
                    salt = salt
                )

                val userId = userDao.insert(newUser)
                auditLogger.logAuthentication(
                    username = trimmedUsername,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.SUCCESS
                )
                Timber.i("Registration successful for user: $trimmedUsername, userId: $userId")
                _registrationState.value = RegistrationState.Success(userId)
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = trimmedUsername,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Registration failed: ${e.message}"
                )
                Timber.e(e, "Registration failed for user: $trimmedUsername")
                _registrationState.value = RegistrationState.Error(
                    R.string.registration_failed,
                    e.localizedMessage
                )
            }
        }
    }

    /**
     * ✅ FIXED: Logout current user
     * Retrieves userId from SessionManager, audits logout, then ends session
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ✅ FIXED: Get current user from SessionManager
                val currentUserId = sessionManager.getCurrentUserId()
                if (currentUserId != null) {
                    val user = userDao.getUser(currentUserId)
                    user?.let {
                        auditLogger.logAuthentication(
                            username = it.username,
                            eventType = AuditEventType.LOGOUT,
                            outcome = AuditOutcome.SUCCESS
                        )
                    }
                }
                sessionManager.endSession("User logout")
                Timber.i("Logout successful")
            } catch (e: Exception) {
                Timber.e(e, "Error during logout")
            }
            _authState.value = AuthState.Idle
        }
    }

    /**
     * Rotate database encryption key manually
     * Generates new passphrase and stores it - requires app restart to take effect
     * Only call from explicit user action (settings button), not automatically
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = KeyRotationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPassphrase = KeystorePassphraseManager.generateNewPassphrase()
                val currentPassphrase = KeystorePassphraseManager.getCurrentPassphrase(context)

                if (currentPassphrase != null) {
                    // Backup and commit new passphrase
                    val success = KeystorePassphraseManager.commitNewPassphrase(context, newPassphrase)
                    if (success) {
                        Timber.i("✅ New passphrase generated - will take effect on next app launch")
                        _keyRotationState.value = KeyRotationState.Success
                    } else {
                        throw Exception("Failed to store new passphrase")
                    }
                } else {
                    // First time initialization
                    KeystorePassphraseManager.getOrCreatePassphrase(context)
                    _keyRotationState.value = KeyRotationState.Success
                }
            } catch (e: Exception) {
                Timber.e(e, "Key rotation failed")
                _keyRotationState.value = KeyRotationState.Error(
                    "Key rotation failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear authentication state
     * Called after navigation or to reset error states
     */
    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }

    /**
     * Clear registration state
     * Called after navigation or to reset error states
     */
    fun clearRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    /**
     * Clear key rotation state
     * Called after displaying rotation result to user
     */
    fun clearKeyRotationState() {
        _keyRotationState.value = KeyRotationState.Idle
    }

    // ========================================
    // Private helper methods
    // ========================================

    /**
     * Hash password using Argon2id with secure parameters
     *
     * @param password Plaintext password
     * @param salt 16-byte salt
     * @return 32-byte password hash
     */
    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val hashResult = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.toByteArray(StandardCharsets.UTF_8),
            salt = salt,
            tCostInIterations = 5,
            mCostInKibibyte = 65536, // 64MB
            parallelism = 2,
            hashLengthInBytes = 32,
            version = Argon2Version.V13
        )
        return hashResult.rawHashAsByteArray()
    }

    /**
     * Generate cryptographically secure random salt
     *
     * @return 16-byte random salt
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Verify password against stored hash using constant-time comparison
     *
     * @param password Plaintext password to verify
     * @param storedHash Stored password hash from database
     * @param salt Salt used for original hash
     * @return true if password matches, false otherwise
     */
    private fun verifyPassword(password: String, storedHash: ByteArray, salt: ByteArray): Boolean {
        val hashToCompare = hashPassword(password, salt)
        return Arrays.equals(storedHash, hashToCompare)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("UserViewModel cleared")
    }
}
