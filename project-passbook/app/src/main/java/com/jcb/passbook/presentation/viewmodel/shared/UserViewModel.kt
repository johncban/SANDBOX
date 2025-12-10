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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.inject.Inject
import java.util.Arrays

private const val TAG = "UserViewModel"

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

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    private val _userId = MutableStateFlow(-1L)
    val userId = _userId.asStateFlow()

    // ✅ FIX: Expose currentUserId (alias to userId for compatibility)
    val currentUserId = _userId.asStateFlow()

    private val _keyRotationState = MutableStateFlow<KeyRotationState>(KeyRotationState.Idle)
    val keyRotationState = _keyRotationState.asStateFlow()

    init {
        Timber.tag(TAG).d("UserViewModel initialized")
    }

    fun setUserId(userId: Long) {
        _userId.value = userId
        Timber.tag(TAG).d("✅ UserId set to: $userId")
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

    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun String.fromBase64(): ByteArray {
        return Base64.decode(this, Base64.NO_WRAP)
    }

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

    fun login(username: String, password: String) {
        Timber.tag(TAG).d("Login attempt for username: '$username'")

        if (username.isBlank() || password.isBlank()) {
            Timber.tag(TAG).w("Login failed: Empty credentials")
            auditLogger.logAuthentication(
                username = username.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                eventType = AuditEventType.AUTHENTICATION_FAILURE,
                outcome = AuditOutcome.FAILURE,
                errorMessage = "Empty credentials"
            )
            _authState.value = AuthState.Error("Username and password cannot be empty")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Querying database for user: '$username'")
                val user = userRepository.getUserByUsername(username)

                if (user != null) {
                    Timber.tag(TAG).d("User found, verifying password...")
                    val storedHashBytes = user.passwordHash.fromBase64()
                    val storedSaltBytes = user.salt.fromBase64()
                    val passwordValid = verifyPassword(password, storedHashBytes, storedSaltBytes)

                    if (passwordValid) {
                        Timber.tag(TAG).i("✓ Login successful for user: ${user.username} (ID: ${user.id})")
                        _userId.value = user.id
                        auditLogger.logAuthentication(
                            username = user.username,
                            eventType = AuditEventType.LOGIN,
                            outcome = AuditOutcome.SUCCESS
                        )
                        _authState.value = AuthState.Success(user.id)
                    } else {
                        Timber.tag(TAG).w("Login failed: Invalid password")
                        auditLogger.logAuthentication(
                            username = username,
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = "Invalid password"
                        )
                        _authState.value = AuthState.Error("Invalid username or password")
                    }
                } else {
                    Timber.tag(TAG).w("Login failed: Username not found")
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Username not found"
                    )
                    _authState.value = AuthState.Error("Invalid username or password")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Login exception")
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

    fun register(username: String, password: String) {
        Timber.tag(TAG).d("Registration attempt for username: '$username'")

        when {
            username.isBlank() -> {
                _registrationState.value = RegistrationState.Error("Username cannot be empty")
                return
            }
            username.length < 3 -> {
                _registrationState.value = RegistrationState.Error("Username must be at least 3 characters")
                return
            }
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> {
                _registrationState.value = RegistrationState.Error("Username can only contain letters, numbers, and underscores")
                return
            }
            password.isBlank() -> {
                _registrationState.value = RegistrationState.Error("Password cannot be empty")
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

                val saltBytes = generateSalt()
                val passwordHashBytes = hashPassword(password, saltBytes)
                val saltString = saltBytes.toBase64()
                val passwordHashString = passwordHashBytes.toBase64()

                val user = User(
                    username = username,
                    passwordHash = passwordHashString,
                    salt = saltString,
                    createdAt = System.currentTimeMillis()
                )

                val userId = userDao.insert(user)
                Timber.tag(TAG).i("✓ Registration successful for username: '$username' (ID: $userId)")
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.SUCCESS
                )

                _userId.value = userId
                _registrationState.value = RegistrationState.Success
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Registration exception")
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
                        sessionManager.endSession("User logout")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during logout")
            } finally {
                _userId.value = -1L
                _authState.value = AuthState.Idle
                Timber.tag(TAG).i("User logged out")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = KeyRotationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPassphrase = KeystorePassphraseManager.generateNewPassphrase()
                val currentPassphrase = KeystorePassphraseManager.getCurrentPassphrase(context)

                if (currentPassphrase != null) {
                    val success = KeystorePassphraseManager.commitNewPassphrase(context, newPassphrase)
                    if (success) {
                        Timber.tag(TAG).i("✅ New passphrase generated")
                        _keyRotationState.value = KeyRotationState.Success
                    } else {
                        throw Exception("Failed to store new passphrase")
                    }
                } else {
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
