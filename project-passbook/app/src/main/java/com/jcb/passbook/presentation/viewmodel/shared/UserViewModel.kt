package com.jcb.passbook.presentation.viewmodel.shared

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.R
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
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

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Success(val userId: Long) : AuthState
    data class Error(@StringRes val messageId: Int, val details: String? = null) : AuthState
}

sealed interface RegistrationState {
    object Idle : RegistrationState
    object Loading : RegistrationState
    object Success : RegistrationState
    data class Error(@StringRes val messageId: Int, val details: String? = null) : RegistrationState
}

// ✅ FIX: Define KeyRotationState (separate from ItemOperationState)
sealed interface KeyRotationState {
    object Idle : KeyRotationState
    object Loading : KeyRotationState
    object Success : KeyRotationState
    data class Error(val message: String) : KeyRotationState
}

@HiltViewModel
class UserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val userRepository: UserRepository,
    private val userDao: UserDao,  // ✅ FIX: Inject UserDao directly
    private val argon2Kt: Argon2Kt,
    private val auditLogger: AuditLogger
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    private val _userId = MutableStateFlow(-1L)
    val userId = _userId.asStateFlow()

    // ✅ FIX: Use KeyRotationState not ItemOperationState
    private val _keyRotationState = MutableStateFlow<KeyRotationState>(KeyRotationState.Idle)
    val keyRotationState = _keyRotationState.asStateFlow()

    fun setUserId(userId: Long) {
        _userId.value = userId
    }

    fun clearKeyRotationState() {
        _keyRotationState.value = KeyRotationState.Idle
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = KeyRotationState.Loading  // ✅ FIX
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPassphrase = KeystorePassphraseManager.rotatePassphrase(context)
                val supportDb = db.openHelper.writableDatabase
                supportDb.query("PRAGMA rekey = '$newPassphrase';").close()
                Arrays.fill(newPassphrase.toCharArray(), ' ')
                _keyRotationState.value = KeyRotationState.Success  // ✅ FIX
            } catch (e: Exception) {
                Timber.e(e, "Database key rotation failed")
                _keyRotationState.value = KeyRotationState.Error("Key rotation failed: ${e.message}")  // ✅ FIX
            }
        }
    }

    // -- Helper to hash using Argon2ID, always returns ByteArray --
    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val hashResult = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.toByteArray(StandardCharsets.UTF_8),
            salt = salt,
            tCostInIterations = 5,
            mCostInKibibyte = 65536,
            parallelism = 2,
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
        if (username.isBlank() || password.isBlank()) {
            auditLogger.logAuthentication(
                username = username.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                eventType = AuditEventType.AUTHENTICATION_FAILURE,
                outcome = "FAILURE",  // ✅ FIX: Use String not AuditOutcome
                errorMessage = "Empty credentials provided"
            )
            _authState.value = AuthState.Error(R.string.error_credentials_empty)
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val user = userRepository.getUserByUsername(username)
                if (user != null && verifyPassword(password, user.passwordHash, user.salt)) {
                    _userId.value = user.id

                    // ✅ FIX: Use logAuthentication instead of logUserEvent
                    auditLogger.logAuthentication(
                        username = user.username,
                        eventType = AuditEventType.LOGIN,
                        outcome = "SUCCESS"  // ✅ FIX: Use String
                    )

                    _authState.value = AuthState.Success(user.id)
                } else {
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = "FAILURE",  // ✅ FIX: Use String
                        errorMessage = "Invalid username or password"
                    )
                    _authState.value = AuthState.Error(R.string.error_invalid_credentials)
                }
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    outcome = "FAILURE",  // ✅ FIX: Use String
                    errorMessage = "Login exception: ${e.message}"
                )
                Timber.e(e, "Login failure")
                _authState.value = AuthState.Error(R.string.error_login_failed, e.localizedMessage)
            }
        }
    }

    fun register(username: String, password: String) {
        when {
            username.isBlank() -> {
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
        viewModelScope.launch {
            try {
                if (userRepository.getUserByUsername(username) != null) {
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.REGISTER,
                        outcome = "FAILURE",  // ✅ FIX: Use String
                        errorMessage = "Username already exists"
                    )
                    _registrationState.value = RegistrationState.Error(R.string.error_username_exists)
                    return@launch
                }

                val salt = generateSalt()
                val passwordHash = hashPassword(password, salt)

                val newUser = User(
                    username = username,
                    passwordHash = passwordHash,
                    salt = salt
                )

                // ✅ FIX: Use userDao.insert() directly (returns Long)
                val userId: Long = userDao.insert(newUser)

                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = "SUCCESS"  // ✅ FIX: Use String
                )

                _userId.value = userId
                _registrationState.value = RegistrationState.Success
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = "FAILURE",  // ✅ FIX: Use String
                    errorMessage = "Registration failed: ${e.message}"
                )
                Timber.e(e, "Registration failed")
                _registrationState.value = RegistrationState.Error(R.string.registration_failed, e.localizedMessage)
            }
        }
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }

    fun clearRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val currentUserId = _userId.value
                if (currentUserId != -1L) {
                    // ✅ FIX: Use userDao.getUser() with explicit type
                    val user: User? = userDao.getUser(currentUserId)

                    // ✅ FIX: Use logAuthentication for logout
                    if (user != null) {
                        auditLogger.logAuthentication(
                            username = user.username,  // ✅ FIX: username property exists
                            eventType = AuditEventType.LOGOUT,
                            outcome = "SUCCESS"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during logout audit logging")
            }
            _userId.value = -1L
            _authState.value = AuthState.Idle
        }
    }
}
