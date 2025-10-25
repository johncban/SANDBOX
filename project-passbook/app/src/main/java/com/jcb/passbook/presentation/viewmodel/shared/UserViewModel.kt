package com.jcb.passbook.presentation.viewmodel.shared


import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.R
import com.jcb.passbook.presentation.viewmodel.vault.ItemOperationState
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.biometric.BiometricLoginManager
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import java.util.Arrays

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Success(val userId: Int) : AuthState
    data class Error(@StringRes val messageId: Int, val details: String? = null) : AuthState
}

// Updated RegistrationState to include userId for biometric setup
sealed interface RegistrationState {
    object Idle : RegistrationState
    object Loading : RegistrationState
    data class Success(val userId: Int) : RegistrationState // Modified to include userId
    data class Error(@StringRes val messageId: Int, val details: String? = null) : RegistrationState
}

@HiltViewModel
class UserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val userRepository: UserRepository,
    private val argon2Kt: Argon2Kt,
    private val auditLogger: AuditLogger
) : ViewModel() {

    companion object {
        private const val PREF_NAME = "passbook_user_prefs"
        private const val PREF_LAST_USER_ID = "last_user_id"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    private val _userId = MutableStateFlow(-1)
    val userId = _userId.asStateFlow()

    private val _keyRotationState = MutableStateFlow<ItemOperationState>(ItemOperationState.Idle)
    val keyRotationState = _keyRotationState.asStateFlow()

    fun setUserId(userId: Int) { _userId.value = userId }
    fun clearKeyRotationState() { _keyRotationState.value = ItemOperationState.Idle }

    // ===== BIOMETRIC USER PERSISTENCE METHODS =====

    /**
     * Save the last logged in user ID for biometric login
     */
    fun saveLastLoggedInUserId(userId: Int) {
        try {
            getSharedPreferences()
                .edit()
                .putInt(PREF_LAST_USER_ID, userId)
                .apply()
            Timber.d("Saved last logged in user ID: $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save last logged in user ID: $userId")
        }
    }

    /**
     * Get the last logged in user ID for biometric login
     */
    fun getLastLoggedInUserId(): Int? {
        return try {
            val prefs = getSharedPreferences()
            val userId = prefs.getInt(PREF_LAST_USER_ID, -1)
            if (userId == -1) null else userId
        } catch (e: Exception) {
            Timber.e(e, "Failed to get last logged in user ID")
            null
        }
    }

    /**
     * Clear the last logged in user ID (call this on logout)
     */
    fun clearLastLoggedInUserId() {
        try {
            getSharedPreferences()
                .edit()
                .remove(PREF_LAST_USER_ID)
                .apply()
            Timber.d("Cleared last logged in user ID")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear last logged in user ID")
        }
    }

    /**
     * Login using biometric token
     * @param token The decrypted biometric token
     */
    fun loginWithBiometricToken(token: ByteArray) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading

                // Hash the token to find the matching user
                val tokenHash = hashToken(token)

                // Find user by token hash
                // Note: You'll need to implement findUserByBiometricTokenHash in UserRepository
                // For now, this is a placeholder that will need database schema updates
                val user = try {
                    userRepository.findUserByBiometricTokenHash(tokenHash)
                } catch (e: Exception) {
                    Timber.w(e, "Biometric token lookup failed - database schema may not be updated yet")
                    null
                }

                if (user != null) {
                    _userId.value = user.id
                    auditLogger.logAuthentication(
                        username = user.username,
                        eventType = AuditEventType.LOGIN,
                        outcome = AuditOutcome.SUCCESS
                    )
                    _authState.value = AuthState.Success(user.id)
                    Timber.d("Biometric login successful for user ${user.id}")
                } else {
                    auditLogger.logAuthentication(
                        username = "UNKNOWN",
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "No user found for biometric token"
                    )
                    _authState.value = AuthState.Error(R.string.biometric_error_no_token)
                    Timber.w("No user found for biometric token")
                }
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = "UNKNOWN",
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Biometric login failed: ${e.message}"
                )
                _authState.value = AuthState.Error(R.string.biometric_error_decrypt_failed)
                Timber.e(e, "Biometric login failed")
            }
        }
    }

    /**
     * Store biometric token hash for a user after registration
     * @param userId The user ID
     * @param token The biometric token to hash and store
     */
    suspend fun storeBiometricTokenForUser(userId: Int, token: ByteArray) {
        try {
            val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val hash = hashTokenWithSalt(token, salt)

            // Update user with biometric token data
            // Note: You'll need to implement updateUserBiometricToken in UserRepository
            userRepository.updateUserBiometricToken(userId, salt, hash)
            Timber.d("Stored biometric token for user $userId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to store biometric token for user $userId")
            throw e
        }
    }

    /**
     * Clear biometric data for a user
     * @param userId The user ID
     */
    fun clearBiometricForUser(userId: Int) {
        viewModelScope.launch {
            try {
                // Clear from local storage
                BiometricLoginManager.clearForUser(context, userId)

                // Clear from database
                userRepository.clearUserBiometricToken(userId)

                Timber.d("Cleared biometric data for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear biometric data for user $userId")
            }
        }
    }

    // ===== AUTHENTICATION METHODS =====

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            auditLogger.logAuthentication(
                username = username.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                eventType = AuditEventType.AUTHENTICATION_FAILURE,
                outcome = AuditOutcome.FAILURE,
                errorMessage = "Empty credentials provided"
            )
            _authState.value = AuthState.Error(R.string.error_credentials_empty)
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val user = userRepository.getUserByUsername(username)
                if (user != null && verifyPassword(password, user.passwordHash)) {
                    // Successful login
                    _userId.value = user.id
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.LOGIN,
                        outcome = AuditOutcome.SUCCESS
                    )
                    _authState.value = AuthState.Success(user.id)
                } else {
                    // Failed login
                    auditLogger.logAuthentication(
                        username = username,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Invalid username or password"
                    )
                    _authState.value = AuthState.Error(R.string.error_invalid_credentials)
                }
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Login exception: ${e.message}"
                )
                Timber.e(e, "Login failed")
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
                        outcome = AuditOutcome.FAILURE,
                        errorMessage = "Username already exists"
                    )
                    _registrationState.value = RegistrationState.Error(R.string.error_username_exists)
                    return@launch
                }

                val passwordHash = hashPassword(password)
                val newUser = User(username = username, passwordHash = passwordHash)
                userRepository.insert(newUser)

                // Get the created user ID for biometric setup
                val createdUser = userRepository.getUserByUsername(username)
                val userId = createdUser?.id ?: 0

                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.SUCCESS
                )

                // Return success with userId for biometric setup
                _registrationState.value = RegistrationState.Success(userId)
            } catch (e: Exception) {
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = "Registration failed: ${e.message}"
                )
                Timber.e(e, "Registration failed")
                _registrationState.value = RegistrationState.Error(R.string.registration_failed, e.localizedMessage)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val currentUserId = _userId.value
                if (currentUserId != -1) {
                    val user = userRepository.getUser(currentUserId).first()
                    auditLogger.logAuthentication(
                        username = user?.username ?: "UNKNOWN",
                        eventType = AuditEventType.LOGOUT,
                        outcome = AuditOutcome.SUCCESS
                    )
                    // Clear biometric data on logout
                    clearLastLoggedInUserId()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during logout audit logging")
            }
        }
        _userId.value = -1
        _authState.value = AuthState.Idle
    }

    // ===== DATABASE KEY ROTATION =====

    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = ItemOperationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPassphrase = KeystorePassphraseManager.rotatePassphrase(context)
                val supportDb = db.openHelper.writableDatabase
                supportDb.query("PRAGMA rekey = '$newPassphrase';").close()
                Arrays.fill(newPassphrase.toCharArray(), ' ')
                _keyRotationState.value = ItemOperationState.Success
            } catch (e: Exception) {
                Timber.e(e, "Database key rotation failed")
                _keyRotationState.value = ItemOperationState.Error("Key rotation failed: ${e.message}")
            }
        }
    }

    // ===== STATE MANAGEMENT =====

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }

    fun clearRegistrationState() {
        _registrationState.value = RegistrationState.Idle
    }

    // ===== PRIVATE HELPER METHODS =====

    private fun getSharedPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private fun verifyPassword(password: String, encodedHash: String): Boolean {
        return argon2Kt.verify(
            mode = Argon2Mode.ARGON2_ID,
            encoded = encodedHash,
            password = password.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val hashResult = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password.toByteArray(StandardCharsets.UTF_8),
            salt = salt,
            tCostInIterations = 5,
            mCostInKibibyte = 65536,
            parallelism = 2,
            version = Argon2Version.V13
        )
        return hashResult.encodedOutputAsString()
    }

    private fun hashToken(token: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token)
    }

    private fun hashTokenWithSalt(token: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(salt, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(token)
    }
}
