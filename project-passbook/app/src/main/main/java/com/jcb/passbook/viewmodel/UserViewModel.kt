package com.jcb.passbook.viewmodel

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.util.CoilUtils.result
import com.jcb.passbook.R
import com.jcb.passbook.repository.UserRepository
import com.jcb.passbook.room.AppDatabase
import com.jcb.passbook.room.User
import com.jcb.passbook.util.security.KeystorePassphraseManager
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



sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Success(val userId: Int) : AuthState
    data class Error(@StringRes val messageId: Int, val details: String? = null) : AuthState
}
sealed interface RegistrationState {
    object Idle : RegistrationState
    object Loading : RegistrationState
    object Success : RegistrationState
    data class Error(@StringRes val messageId: Int, val details: String? = null) : RegistrationState
}

@HiltViewModel
class UserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val userRepository: UserRepository,
    private val argon2Kt: Argon2Kt
) : ViewModel() {

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

    @RequiresApi(Build.VERSION_CODES.M)
    fun rotateDatabaseKey() {
        _keyRotationState.value = ItemOperationState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newPassphrase = KeystorePassphraseManager.rotatePassphrase(context)
                val supportDb = db.openHelper.writableDatabase
                supportDb.query("PRAGMA rekey = '$newPassphrase';").close()
                java.util.Arrays.fill(newPassphrase.toCharArray(), ' ')
                _keyRotationState.value = ItemOperationState.Success
            } catch (e: Exception) {
                Timber.e(e, "Database key rotation failed")
                _keyRotationState.value = ItemOperationState.Error("Key rotation failed: ${e.message}")
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error(R.string.error_credentials_empty)
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val user = userRepository.getUserByUsername(username)
                if (user != null && verifyPassword(password, user.passwordHash)) {
                    _userId.value = user.id
                    _authState.value = AuthState.Success(user.id)
                } else {
                    _authState.value = AuthState.Error(R.string.error_invalid_credentials)
                }
            } catch (e: Exception) {
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
                    _registrationState.value = RegistrationState.Error(R.string.error_username_exists)
                    return@launch
                }
                val passwordHash = hashPassword(password)
                val newUser = User(username = username, passwordHash = passwordHash)
                userRepository.insert(newUser)
                _registrationState.value = RegistrationState.Success
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                _registrationState.value = RegistrationState.Error(R.string.registration_failed, e.localizedMessage)
            }
        }
    }

    fun logout() {
        _userId.value = -1
        _authState.value = AuthState.Idle
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }
    fun clearRegistrationState() {
        _registrationState.value = RegistrationState.Idle
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
}
