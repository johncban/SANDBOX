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
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils
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
    private val userDao: UserDao,
    private val argon2Kt: Argon2Kt,
    private val auditLogger: AuditLogger,
    private val databaseKeyManager: DatabaseKeyManager,  // ✅ INJECT DatabaseKeyManager
    private val secureMemoryUtils: SecureMemoryUtils    // ✅ INJECT SecureMemoryUtils
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val registrationState = _registrationState.asStateFlow()

    private val _userId = MutableStateFlow(-1L)
    val userId = _userId.asStateFlow()

    private val _keyRotationState = MutableStateFlow<KeyRotationState>(KeyRotationState.Idle)
    val keyRotationState = _keyRotationState.asStateFlow()

    fun setUserId(userId: Long) {
        _userId.value = userId
    }

    fun clearKeyRotationState() {
        _keyRotationState.value = KeyRotationState.Idle
    }

    /**
     * ✅ COMPLETELY FIXED: Database key rotation using DatabaseKeyManager
     * This version works with the actual database encryption system
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = KeyRotationState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            var newPassphrase: ByteArray? = null

            try {
                Timber.i("🔄 Starting database key rotation...")

                // Step 1: Get current passphrase from DatabaseKeyManager
                val currentPassphrase = databaseKeyManager.getCurrentDatabasePassphrase()
                if (currentPassphrase == null) {
                    throw IllegalStateException("Cannot rotate: no current database key found in DatabaseKeyManager")
                }
                Timber.d("✅ Retrieved current database key (${currentPassphrase.size} bytes)")

                // Step 2: Verify database can be opened with current passphrase
                try {
                    val testDb = db.openHelper.writableDatabase
                    testDb.beginTransaction()
                    testDb.query("SELECT COUNT(*) FROM sqlite_master").use { cursor ->
                        if (cursor.moveToFirst()) {
                            Timber.d("✅ Database accessible with current key")
                        }
                    }
                    testDb.setTransactionSuccessful()
                    testDb.endTransaction()
                } catch (e: Exception) {
                    throw IllegalStateException("Database not accessible with current key", e)
                }

                // Step 3: Generate NEW passphrase (NOT stored yet)
                newPassphrase = databaseKeyManager.generateNewDatabasePassphrase()
                Timber.d("✅ Generated new database key (${newPassphrase.size} bytes)")

                // Step 4: Convert ByteArray to hex string for SQLCipher
                val currentPassphraseHex = "x'${currentPassphrase.joinToString("") { "%02x".format(it) }}'"
                val newPassphraseHex = "x'${newPassphrase.joinToString("") { "%02x".format(it) }}'"

                // Step 5: Close any open connections first
                db.close()

                // Step 6: Reopen with current passphrase
                val dbPath = context.getDatabasePath("passbook.db").absolutePath
                val tempDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                    dbPath,
                    currentPassphraseHex,
                    null,
                    net.sqlcipher.database.SQLiteDatabase.OPEN_READWRITE
                )

                // Step 7: Execute PRAGMA rekey
                tempDb.execSQL("PRAGMA rekey = $newPassphraseHex")
                Timber.d("✅ PRAGMA rekey executed")

                // CRITICAL: Close and verify rekey succeeded
                tempDb.close()

                // Step 8: Verify rekey succeeded by opening with new passphrase
                val verifyDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                    dbPath,
                    newPassphraseHex,
                    null,
                    net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
                )

                verifyDb.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        throw IllegalStateException("Rekey verification failed: cannot read database")
                    }
                    Timber.d("✅ Rekey verification successful - database readable with new key")
                }
                verifyDb.close()

                // Step 9: ONLY NOW commit the new passphrase to storage
                val commitSuccess = databaseKeyManager.commitNewDatabasePassphrase(newPassphrase)
                if (!commitSuccess) {
                    throw IllegalStateException("Failed to commit new passphrase to storage")
                }
                Timber.i("✅ New database key committed to storage")

                // Step 10: Clear backup after successful rotation
                databaseKeyManager.clearBackup()

                // Clear sensitive data
                secureMemoryUtils.wipeByteArray(currentPassphrase)
                secureMemoryUtils.wipeByteArray(newPassphrase)

                _keyRotationState.value = KeyRotationState.Success
                Timber.i("✅✅✅ Database key rotation completed successfully!")

            } catch (e: Exception) {
                Timber.e(e, "❌ Database key rotation FAILED")

                // CRITICAL: Rollback if new passphrase was committed
                if (newPassphrase != null) {
                    Timber.w("Attempting rollback...")
                    val rollbackSuccess = databaseKeyManager.rollbackToBackup()
                    if (rollbackSuccess) {
                        Timber.i("✅ Rollback successful - original key restored")
                    } else {
                        Timber.e("❌ CRITICAL: Rollback failed - manual recovery may be needed")
                    }
                }

                // Clear sensitive data
                newPassphrase?.let { secureMemoryUtils.wipeByteArray(it) }

                _keyRotationState.value = KeyRotationState.Error(
                    "Key rotation failed: ${e.message}. " +
                            "${if (newPassphrase != null) "Original key restored." else "No changes made."}"
                )
            }
        }
    }

    /**
     * ✅ FIXED: Emergency recovery function using DatabaseKeyManager
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun attemptDatabaseRecovery() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.w("🆘 Attempting emergency database recovery...")

                // Try to rollback to backup passphrase
                val rollbackSuccess = databaseKeyManager.rollbackToBackup()

                if (rollbackSuccess) {
                    Timber.i("✅ Backup key restored")

                    // Try to open database with restored passphrase
                    val passphrase = databaseKeyManager.getCurrentDatabasePassphrase()
                    if (passphrase != null) {
                        try {
                            val passphraseHex = "x'${passphrase.joinToString("") { "%02x".format(it) }}'"
                            val testDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                                context.getDatabasePath("passbook.db").absolutePath,
                                passphraseHex,
                                null,
                                net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
                            )
                            testDb.rawQuery("SELECT COUNT(*) FROM sqlite_master", null).use { it.moveToFirst() }
                            testDb.close()

                            Timber.i("✅✅ Recovery successful! Database is accessible again.")
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Database still inaccessible after recovery attempt")
                        }
                    }
                } else {
                    Timber.e("❌ No backup available for recovery")
                }

            } catch (e: Exception) {
                Timber.e(e, "Emergency recovery failed")
            }
        }
    }

    // Keep all your existing login, register, logout methods unchanged...
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
                if (user != null && verifyPassword(password, user.passwordHash, user.salt)) {
                    _userId.value = user.id
                    auditLogger.logAuthentication(
                        username = user.username,
                        eventType = AuditEventType.LOGIN,
                        outcome = AuditOutcome.SUCCESS
                    )
                    _authState.value = AuthState.Success(user.id)
                } else {
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
                        outcome = AuditOutcome.FAILURE,
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

                val userId: Long = userDao.insert(newUser)
                auditLogger.logAuthentication(
                    username = username,
                    eventType = AuditEventType.REGISTER,
                    outcome = AuditOutcome.SUCCESS
                )
                _userId.value = userId
                _registrationState.value = RegistrationState.Success

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
                    val user: User? = userDao.getUser(currentUserId)
                    if (user != null) {
                        auditLogger.logAuthentication(
                            username = user.username,
                            eventType = AuditEventType.LOGOUT,
                            outcome = AuditOutcome.SUCCESS
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
