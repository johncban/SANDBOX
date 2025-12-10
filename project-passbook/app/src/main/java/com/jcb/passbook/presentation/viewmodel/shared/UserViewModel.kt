package com.jcb.passbook.presentation.viewmodel.shared

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.SessionManager
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.inject.Inject

private const val TAG = "UserViewModel"

/**
 * Sealed Interfaces for State Management
 */
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
 * UserViewModel - Production-Ready Implementation with Compatibility Methods
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

    /**
     * State Flows
     */
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    private val _userId = MutableStateFlow(-1L)
    val userId = _userId.asStateFlow()

    private val _keyRotationState = MutableStateFlow<KeyRotationState>(KeyRotationState.Idle)
    val keyRotationState = _keyRotationState.asStateFlow()

    /**
     * Compatibility property: Exposes userId as Int StateFlow for screens expecting Int
     */
    val currentUserId: StateFlow<Int> = userId.map { it.toInt() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    init {
        Timber.tag(TAG).d("UserViewModel initialized")
        // Load the current user ID immediately
        loadCurrentUserId()
    }

    /**
     * NEW: Load the current user ID from UserRepository/DataStore
     */
    private fun loadCurrentUserId() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Try to get stored user ID from repository
                userRepository.getCurrentUserId().collect { storedUserId ->
                    if (storedUserId > 0) {
                        _userId.value = storedUserId.toLong()
                        Timber.tag(TAG).d("Loaded userId from DataStore: $storedUserId")
                    } else {
                        // No user stored, check if any user exists in database
                        val existingUser = userDao.getAllUsers().firstOrNull()
                        if (existingUser != null) {
                            // Found a user in database, set it as current
                            _userId.value = existingUser.id
                            userRepository.setCurrentUserId(existingUser.id.toInt())
                            Timber.tag(TAG).i("Found existing user in database, set as current: ${existingUser.id}")
                        } else {
                            _userId.value = -1L
                            Timber.tag(TAG).d("No user found in DataStore or database")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error loading current user ID")
                _userId.value = -1L
            }
        }
    }

    /**
     * COMPATIBILITY METHODS for ItemListScreen and AddItemScreen
     */

    /**
     * Get current user ID as Flow<Int> for compatibility with screens expecting Int
     */
    fun getCurrentUserId(): Flow<Int> {
        return userId.map { it.toInt() }
    }

    /**
     * Set current user ID from Int parameter (compatibility method)
     */
    fun setCurrentUserId(userId: Int) {
        setUserId(userId.toLong())
        // Also persist to DataStore
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userRepository.setCurrentUserId(userId)
                Timber.tag(TAG).d("Persisted userId to DataStore: $userId")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error persisting userId to DataStore")
            }
        }
    }

    /**
     * Clear current user ID (compatibility method - calls logout)
     */
    fun clearCurrentUserId() {
        logout()
    }

    /**
     * Check if a valid user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        val loggedIn = _userId.value > 0
        Timber.tag(TAG).d("isUserLoggedIn: $loggedIn (userId: ${_userId.value})")
        return loggedIn
    }

    /**
     * EXISTING PUBLIC STATE MANAGEMENT FUNCTIONS
     */

    fun setUserId(userId: Long) {
        _userId.value = userId
        Timber.tag(TAG).d("UserId set to: $userId")
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
        Timber.tag(TAG).d("Auth state cleared")
    }

    fun clearRegistrationState() {
        _registrationState.value = RegistrationState.Idle
        Timber.tag(TAG).d("Registration state cleared")
    }

    fun clearKeyRotationState() {
        _keyRotationState.value = KeyRotationState.Idle
        Timber.tag(TAG).d("Key rotation state cleared")
    }

    /**
     * Base64 Encoding/Decoding Helper Functions
     */
    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

    /**
     * Password Hashing Functions (Argon2id)
     */
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

    private fun verifyPassword(password: String, storedHash: ByteArray, salt: ByteArray): Boolean {
        val hashToCompare = hashPassword(password, salt)
        return Arrays.equals(storedHash, hashToCompare)
    }

    /**
     * Login Function with Comprehensive Error Handling
     */
    fun login(username: String, password: String) {
        Timber.tag(TAG).d("Login attempt for username: $username")

        // Pre-validation
        if (username.isBlank() || password.isBlank()) {
            Timber.tag(TAG).w("Login failed: Empty credentials provided")
            auditLogger.logAuthentication(
                username = username.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                eventType = AuditEventType.AUTHENTICATION_FAILURE,
                outcome = AuditOutcome.FAILURE,
                errorMessage = "Empty credentials provided"
            )
            _authState.value = AuthState.Error("Username and password cannot be empty")
            return
        }

        _authState.value = AuthState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Querying database for user: $username")
                val user = userRepository.getUserByUsername(username)

                if (user != null) {
                    Timber.tag(TAG).d("User found in database, verifying password...")

                    val storedHashBytes = user.passwordHash.fromBase64()
                    val storedSaltBytes = user.salt.fromBase64()
                    val passwordValid = verifyPassword(password, storedHashBytes, storedSaltBytes)

                    if (passwordValid) {
                        // SUCCESS
                        Timber.tag(TAG).i("Login successful for user: ${user.username} (ID: ${user.id})")
                        _userId.value = user.id

                        // Persist to DataStore
                        userRepository.setCurrentUserId(user.id.toInt())

                        auditLogger.logAuthentication(
                            username = user.username,
                            eventType = AuditEventType.LOGIN,
                            outcome = AuditOutcome.SUCCESS
                        )
                        _authState.value = AuthState.Success(user.id)
                    } else {
                        // FAILURE: Password incorrect
                        Timber.tag(TAG).w("Login failed: Invalid password for username: $username")
                        auditLogger.logAuthentication(
                            username = username,
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = "Invalid password"
                        )
                        _authState.value = AuthState.Error("Invalid username or password")
                    }
                } else {
                    // FAILURE: User not found
                    Timber.tag(TAG).w("Login failed: Username not found: $username")
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Username not found"
                    )
                    _authState.value = AuthState.Error("Invalid username or password")
                }
            } catch (e: Exception) {
                // EXCEPTION
                Timber.tag(TAG).e(e, "Login exception for username: $username")
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Login exception: ${e.message}"
                )
                _authState.value = AuthState.Error(
                    message = "Login failed: ${e.localizedMessage ?: "Unknown error"}",
                    details = e.message
                )
            }
        }
    }

    /**
     * Registration Function with Direct String Messages
     */
    fun register(username: String, password: String) {
        Timber.tag(TAG).d("Registration attempt for username: $username")

        // Pre-validation
        when {
            username.isBlank() -> {
                Timber.tag(TAG).w("Registration failed: Empty username")
                _registrationState.value = RegistrationState.Error("Username cannot be empty")
                return
            }
            username.length < 3 -> {
                Timber.tag(TAG).w("Registration failed: Username too short")
                _registrationState.value = RegistrationState.Error("Username must be at least 3 characters")
                return
            }
            !username.matches(Regex("[a-zA-Z0-9_]+")) -> {
                Timber.tag(TAG).w("Registration failed: Invalid username characters")
                _registrationState.value = RegistrationState.Error("Username can only contain letters, numbers, and underscores")
                return
            }
            password.isBlank() -> {
                Timber.tag(TAG).w("Registration failed: Empty password")
                _registrationState.value = RegistrationState.Error("Password cannot be empty")
                return
            }
            password.length < 8 -> {
                Timber.tag(TAG).w("Registration failed: Password too short")
                _registrationState.value = RegistrationState.Error("Password must be at least 8 characters")
                return
            }
        }

        _registrationState.value = RegistrationState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if username already exists
                if (userRepository.getUserByUsername(username) != null) {
                    Timber.tag(TAG).w("Registration failed: Username '$username' already exists")
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.REGISTER,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Username already exists"
                    )
                    _registrationState.value = RegistrationState.Error("Username already exists")
                    return@launch
                }

                // Generate salt and hash password
                val saltBytes = generateSalt()
                val passwordHashBytes = hashPassword(password, saltBytes)

                val saltString = saltBytes.toBase64()
                val passwordHashString = passwordHashBytes.toBase64()

                // Create user object
                val user = User(
                    username = username,
                    passwordHash = passwordHashString,
                    salt = saltString,
                    createdAt = System.currentTimeMillis()
                )

                // Insert user into database
                val userId = userDao.insert(user)
                Timber.tag(TAG).i("Registration successful for username: $username (ID: $userId)")

                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.SUCCESS
                )

                _userId.value = userId

                // Persist to DataStore
                userRepository.setCurrentUserId(userId.toInt())

                _registrationState.value = RegistrationState.Success
            } catch (e: Exception) {
                // EXCEPTION: Unexpected error during registration
                Timber.tag(TAG).e(e, "Registration exception for username: $username")
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Registration exception: ${e.message}"
                )
                _registrationState.value = RegistrationState.Error(
                    message = "Registration failed: ${e.localizedMessage ?: "Unknown error"}",
                    details = e.message
                )
            }
        }
    }

    /**
     * Logout Function
     */
    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUserId = _userId.value
                if (currentUserId != -1L) {
                    val user: User? = userDao.getUser(currentUserId)
                    if (user != null) {
                        auditLogger.logAuthentication(
                            username = user.username,
                            eventType = AuditEventType.LOGOUT,
                            outcome = AuditOutcome.SUCCESS
                        )
                    }
                }

                // End session (wipes keys from memory)
                sessionManager.endSession("User logout")

                // Clear from DataStore
                userRepository.clearCurrentUserId()

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during logout")
            } finally {
                _userId.value = -1L
                _authState.value = AuthState.Idle
                Timber.tag(TAG).i("User logged out")
            }
        }
    }

    /**
     * Database Key Rotation (Manual Only)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = KeyRotationState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Generate new passphrase
                val newPassphrase = KeystorePassphraseManager.generateNewPassphrase()

                // Backup current passphrase
                val currentPassphrase = KeystorePassphraseManager.getCurrentPassphrase(context)

                if (currentPassphrase != null) {
                    // Store new passphrase
                    val success = KeystorePassphraseManager.commitNewPassphrase(context, newPassphrase)
                    if (success) {
                        Timber.tag(TAG).i("New passphrase generated - will take effect on next app launch")
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
                Timber.tag(TAG).e(e, "Key rotation failed")
                _keyRotationState.value = KeyRotationState.Error("Key rotation failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.tag(TAG).d("UserViewModel cleared")
    }
}
