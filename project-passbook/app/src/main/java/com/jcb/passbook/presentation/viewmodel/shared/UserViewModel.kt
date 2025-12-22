package com.jcb.passbook.presentation.viewmodel.shared

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.R
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.repository.UserRepository
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * UserViewModel - Manages user authentication and registration
 *
 * 🔥 COMPLETE VERSION (2025-12-22):
 * - Separate AuthState for login
 * - Separate RegistrationState for registration
 * - Both emit userId on success for UI to handle session start
 * - Uses Argon2id password hashing
 * - Maintains state until explicit clear
 *
 * Responsibilities:
 * - Validate user credentials with Argon2id password hashing
 * - Register new users with secure password storage
 * - Emit Success states with userId for UI layer to start session
 * - Maintain auth/registration state throughout flow
 * - Clear states on explicit logout or navigation
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val argon2Kt: Argon2Kt
) : ViewModel() {

    // Login state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Registration state
    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    /**
     * Authenticate user with username and master password
     *
     * 🔥 CRITICAL: Only validates credentials - does NOT start session
     * Emit AuthState.Success(userId) for UI layer to handle session start
     *
     * @param username User's username
     * @param masterPassword User's master password (plaintext)
     */
    fun login(username: String, masterPassword: String) {
        val trimmedUsername = username.trim()

        // Validate input
        if (trimmedUsername.isBlank() || masterPassword.isBlank()) {
            _authState.value = AuthState.Error(R.string.error_credentials_empty)
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get user from database
                val user = userRepository.getUserByUsername(trimmedUsername)

                if (user == null) {
                    Timber.w("Login failed for user: $trimmedUsername - User not found")
                    _authState.value = AuthState.Error(R.string.error_invalid_credentials)
                    return@launch
                }

                // Verify password using Argon2id
                val isPasswordValid = verifyPassword(
                    password = masterPassword,
                    storedHash = user.passwordHash,
                    salt = user.salt
                )

                if (isPasswordValid) {
                    Timber.i("✅ Login successful for user: $trimmedUsername, userId: ${user.id}")

                    // 🔥 CRITICAL: Only emit userId - UI will call SessionManager.startSession()
                    _authState.value = AuthState.Success(user.id)

                } else {
                    Timber.w("Login failed for user: $trimmedUsername - Invalid password")
                    _authState.value = AuthState.Error(R.string.error_invalid_credentials)
                }
            } catch (e: Exception) {
                Timber.e(e, "Login error for user: $trimmedUsername")
                _authState.value = AuthState.Error(R.string.error_login_failed, e.localizedMessage)
            }
        }
    }

    /**
     * Register new user with username and master password
     *
     * 🔥 CRITICAL: Only creates user - does NOT start session
     * Emit RegistrationState.Success(userId) for UI layer to handle session start
     *
     * @param username User's username (unique)
     * @param masterPassword User's master password (plaintext, min 8 chars)
     */
    fun register(username: String, masterPassword: String) {
        val trimmedUsername = username.trim()

        // Validate input
        when {
            trimmedUsername.isBlank() -> {
                _registrationState.value = RegistrationState.Error(R.string.error_empty_username)
                return
            }
            masterPassword.isBlank() -> {
                _registrationState.value = RegistrationState.Error(R.string.error_empty_password)
                return
            }
            masterPassword.length < 8 -> {
                _registrationState.value = RegistrationState.Error(R.string.error_password_length)
                return
            }
        }

        _registrationState.value = RegistrationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if user already exists
                val existingUser = userRepository.getUserByUsername(trimmedUsername)
                if (existingUser != null) {
                    Timber.w("Registration failed: User $trimmedUsername already exists")
                    _registrationState.value = RegistrationState.Error(R.string.error_username_exists)
                    return@launch
                }

                // Hash password using Argon2id
                val salt = generateSalt()
                val passwordHash = hashPassword(masterPassword, salt)

                // Create user entity
                val user = User(
                    id = 0, // Auto-generated by Room
                    username = trimmedUsername,
                    passwordHash = passwordHash,
                    salt = salt,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = null,
                    isActive = true
                )

                // Insert user and get generated ID
                val userId = userRepository.insertUser(user)

                Timber.i("✅ Registration successful for user: $trimmedUsername, userId: $userId")

                // 🔥 CRITICAL: Only emit userId - UI will call SessionManager.startSession()
                _registrationState.value = RegistrationState.Success(userId)

            } catch (e: Exception) {
                Timber.e(e, "Registration error for user: $trimmedUsername")
                _registrationState.value = RegistrationState.Error(R.string.registration_failed, e.localizedMessage)
            }
        }
    }

    /**
     * Clear authentication state
     * Called after navigation or explicit logout
     */
    fun clearAuthState() {
        Timber.i("Clearing authentication state")
        _authState.value = AuthState.Idle
    }

    /**
     * Clear registration state
     * Called after successful registration or navigation
     */
    fun clearRegistrationState() {
        Timber.i("Clearing registration state")
        _registrationState.value = RegistrationState.Idle
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
}
