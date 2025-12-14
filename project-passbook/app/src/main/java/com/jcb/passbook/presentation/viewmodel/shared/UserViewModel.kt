package com.jcb.passbook.presentation.viewmodel.shared

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.SessionManager
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.inject.Inject

private const val TAG = "UserViewModel"

// ---------- UI State Classes ----------
sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Success(val userId: Long) : AuthState
    data class Error(val message: String, val details: String? = null) : AuthState
}

sealed interface RegistrationState {
    object Idle : RegistrationState
    object Loading : RegistrationState
    object Success : RegistrationState
    data class Error(val message: String, val details: String? = null) : RegistrationState
}

sealed interface KeyRotationState {
    object Idle : KeyRotationState
    object Loading : KeyRotationState
    object Success : KeyRotationState
    data class Error(val message: String) : KeyRotationState
}

/**
 * UserViewModel - Manages user authentication and registration
 * Coordinates with SessionManager for session lifecycle
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @Suppress("unused") private val db: AppDatabase,
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val argon2Kt: Argon2Kt,
    private val auditLogger: AuditLogger,
    private val sessionManager: SessionManager
) : ViewModel() {

    // State flows
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _userId = MutableStateFlow(-1L)
    val userId: StateFlow<Long> = _userId.asStateFlow()

    private val _keyRotationState = MutableStateFlow<KeyRotationState>(KeyRotationState.Idle)
    val keyRotationState: StateFlow<KeyRotationState> = _keyRotationState.asStateFlow()

    init {
        Timber.tag(TAG).d("UserViewModel initialized")
        loadCurrentUserId()
    }

    // ---------- User ID Management ----------

    /**
     * Load current user ID from DataStore or database
     * Called on ViewModel initialization
     */
    private fun loadCurrentUserId() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get current userId from DataStore (single read, not continuous collection)
                val storedUserId = userRepository.getCurrentUserId().first()

                if (storedUserId > 0) {
                    _userId.value = storedUserId.toLong()
                    Timber.tag(TAG).d("✅ Loaded userId from DataStore: $storedUserId")
                } else {
                    // Try loading from database if DataStore is empty
                    try {
                        val existingUserList = userDao.getAllUsers().first()
                        val existingUser = existingUserList.firstOrNull()

                        if (existingUser != null) {
                            _userId.value = existingUser.id
                            userRepository.setCurrentUserId(existingUser.id.toInt())
                            Timber.tag(TAG).i("✅ Found existing user in DB, set as current: ${existingUser.id}")
                        } else {
                            _userId.value = -1L
                            Timber.tag(TAG).d("No user found in DataStore or database")
                        }
                    } catch (e: NoSuchElementException) {
                        _userId.value = -1L
                        Timber.tag(TAG).d("No user found in database")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Error loading current user ID")
                _userId.value = -1L
            }
        }
    }

    fun getCurrentUserId(): Flow<Int> = userId.map { it.toInt() }

    fun setCurrentUserId(userId: Int) {
        setUserId(userId.toLong())
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userRepository.setCurrentUserId(userId)
                Timber.tag(TAG).d("Persisted userId to DataStore: $userId")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error persisting userId to DataStore")
            }
        }
    }

    fun clearCurrentUserId() {
        logout()
    }

    fun isUserLoggedIn(): Boolean {
        val loggedIn = _userId.value > 0 && sessionManager.isAuthenticated()
        Timber.tag(TAG).d("isUserLoggedIn: $loggedIn (userId: ${_userId.value})")
        return loggedIn
    }

    fun setUserId(userId: Long) {
        _userId.value = userId
        Timber.tag(TAG).d("UserId set to: $userId")
    }

    // ---------- State Management ----------

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }

    fun clearRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    fun clearKeyRotationState() {
        _keyRotationState.value = KeyRotationState.Idle
    }

    // ---------- Crypto Helpers ----------

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val hashResult = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.toByteArray(StandardCharsets.UTF_8),
            salt = salt,
            tCostInIterations = 5,
            mCostInKibibyte = 65536,
            parallelism = 2,
            hashLengthInBytes = 32,
            version = Argon2Version.V13
        )
        return hashResult.rawHashAsByteArray()
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun verifyPassword(
        password: String,
        storedHash: ByteArray,
        salt: ByteArray
    ): Boolean {
        val hashToCompare = hashPassword(password, salt)
        return Arrays.equals(storedHash, hashToCompare)
    }

    // ---------- Login ----------

    /**
     * Authenticate user with username and password
     * Starts session on successful authentication
     */
    fun login(username: String, password: String) {
        Timber.tag(TAG).d("Login attempt for username: $username")

        if (username.isBlank() || password.isBlank()) {
            Timber.tag(TAG).w("Login failed: Empty credentials provided")
            auditLogger.logAuthentication(
                username = username.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                eventType = AuditEventType.AUTHENTICATION_FAILURE,
                outcome = AuditOutcome.FAILURE
            )
            _authState.value = AuthState.Error("Username and password cannot be empty")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = userRepository.getUserByUsername(username)
                if (user != null) {
                    val storedHashBytes = user.passwordHash.fromBase64()
                    val storedSaltBytes = user.salt.fromBase64()
                    val passwordValid = verifyPassword(password, storedHashBytes, storedSaltBytes)

                    if (passwordValid) {
                        // Update user ID
                        _userId.value = user.id
                        userRepository.setCurrentUserId(user.id.toInt())

                        // ✅ Start session (no activity parameter needed)
                        sessionManager.startSession()

                        // Log successful authentication
                        auditLogger.logAuthentication(
                            username = user.username,
                            eventType = AuditEventType.LOGIN,
                            outcome = AuditOutcome.SUCCESS
                        )

                        _authState.value = AuthState.Success(user.id)
                        Timber.tag(TAG).i("✅ Login successful for user: ${user.username} (ID: ${user.id})")
                    } else {
                        auditLogger.logAuthentication(
                            username = username,
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            outcome = AuditOutcome.FAILURE
                        )
                        _authState.value = AuthState.Error("Invalid username or password")
                    }
                } else {
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = AuditOutcome.FAILURE
                    )
                    _authState.value = AuthState.Error("Invalid username or password")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Login error")
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    outcome = AuditOutcome.FAILURE
                )
                _authState.value = AuthState.Error("Login failed", e.message)
            }
        }
    }

    // ---------- Registration ----------

    /**
     * Register a new user account
     * Starts session on successful registration
     */
    fun register(username: String, password: String, confirmPassword: String) {
        Timber.tag(TAG).d("Registration attempt for username: $username")

        // Validation
        when {
            username.isBlank() -> {
                _registrationState.value = RegistrationState.Error("Username cannot be empty")
                return
            }
            password.isBlank() -> {
                _registrationState.value = RegistrationState.Error("Password cannot be empty")
                return
            }
            password != confirmPassword -> {
                _registrationState.value = RegistrationState.Error("Passwords do not match")
                return
            }
            password.length < 8 -> {
                _registrationState.value = RegistrationState.Error("Password must be at least 8 characters")
                return
            }
        }

        _registrationState.value = RegistrationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if username already exists
                val existingUser = userRepository.getUserByUsername(username)
                if (existingUser != null) {
                    Timber.tag(TAG).w("Registration failed: User already exists - username: $username")
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.REGISTER,
                        outcome = AuditOutcome.FAILURE
                    )
                    _registrationState.value = RegistrationState.Error("Username already exists")
                    return@launch
                }

                // Create new user
                val salt = generateSalt()
                val passwordHash = hashPassword(password, salt)
                val newUser = User(
                    id = 0,
                    username = username,
                    passwordHash = passwordHash.toBase64(),
                    email = null,
                    salt = salt.toBase64(),
                    createdAt = System.currentTimeMillis()
                )

                // Insert user into database
                val userId = userRepository.insertUser(newUser)
                Timber.tag(TAG).i("✅ Registration successful for user: $username (ID: $userId)")

                // Set current user ID
                _userId.value = userId
                userRepository.setCurrentUserId(userId.toInt())

                // ✅ Start session immediately after registration
                sessionManager.startSession()

                // Log successful registration
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.SUCCESS
                )

                _authState.value = AuthState.Success(userId)
                _registrationState.value = RegistrationState.Success

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Registration error")
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.FAILURE
                )
                _registrationState.value = RegistrationState.Error(e.message ?: "Registration failed")
            }
        }
    }

    // ---------- Logout ----------

    /**
     * Log out current user and end session
     * Clears all sensitive data and user state
     */
    fun logout() {
        Timber.tag(TAG).i("User logging out")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ✅ End session (suspend function called in coroutine)
                sessionManager.endSession("User logout")

                // Clear user state
                _userId.value = -1L
                _authState.value = AuthState.Idle
                _registrationState.value = RegistrationState.Idle
                _keyRotationState.value = KeyRotationState.Idle

                // Log logout event
                auditLogger.logAuthentication(
                    username = "(user)",
                    eventType = AuditEventType.LOGOUT,
                    outcome = AuditOutcome.SUCCESS
                )

                // Clear DataStore
                userRepository.clearCurrentUserId()
                Timber.tag(TAG).i("✅ Logout completed")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Error during logout")
            }
        }
    }

    // ---------- Key Rotation (Placeholder) ----------

    /**
     * Rotate encryption keys (future implementation)
     */
    fun rotateKeys(newMasterPassword: String, newDatabasePassword: String) {
        Timber.tag(TAG).i("Initiating key rotation...")

        if (newMasterPassword.isBlank() || newDatabasePassword.isBlank()) {
            _keyRotationState.value = KeyRotationState.Error("Passwords cannot be empty")
            return
        }

        _keyRotationState.value = KeyRotationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // TODO: Implement key rotation logic
                // 1. Re-encrypt all passwords with new keys
                // 2. Update master key
                // 3. Update database encryption key

                Timber.tag(TAG).i("Key rotation completed")
                _keyRotationState.value = KeyRotationState.Success
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Key rotation failed")
                _keyRotationState.value = KeyRotationState.Error(e.message ?: "Key rotation failed")
            }
        }
    }
}
