package com.jcb.passbook.presentation.viewmodel.auth


import android.content.Context
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.security.biometric.BiometricHelper
import com.jcb.passbook.security.crypto.SessionPassphraseManager
import com.jcb.passbook.security.session.SessionManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * ViewModel for UnlockScreen managing biometric and password-based vault unlocking
 */
@HiltViewModel
class UnlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val sessionPassphraseManager: SessionPassphraseManager,
    private val auditLogger: AuditLogger
) : ViewModel() {

    companion object {
        private const val TAG = "UnlockViewModel"
    }

    data class UnlockUiState(
        val isLoading: Boolean = false,
        val isUnlocked: Boolean = false,
        val biometricAvailable: Boolean = false,
        val biometricPrompted: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        checkBiometricAvailability()
        initializeMasterSeed()
        sessionManager.startUnlock()

        // Log unlock attempt using proper method
        auditLogger.logAuthenticationEvent(
            eventType = AuditEventType.LOGIN,
            username = "SYSTEM",
            outcome = AuditOutcome.WARNING,
            userId = null,
            errorMessage = "Vault unlock attempt started"
        )
    }

    private fun checkBiometricAvailability() {
        val availability = BiometricHelper.checkAvailability(context)
        val biometricAvailable = availability == BiometricHelper.Availability.AVAILABLE

        _uiState.value = _uiState.value.copy(
            biometricAvailable = biometricAvailable
        )

        Timber.d("Biometric availability: $availability")
    }

    private fun initializeMasterSeed() {
        viewModelScope.launch {
            try {
                if (!sessionPassphraseManager.isMasterSeedInitialized()) {
                    Timber.d("Initializing master seed")
                    sessionPassphraseManager.initializeMasterSeed()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize master seed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to initialize security components"
                )
            }
        }
    }

    /**
     * Creates cipher for biometric authentication
     */
    fun createBiometricCipher(): Cipher? {
        return try {
            // For simplicity, we'll use non-crypto biometric for now
            // In production, this should create a cipher with the biometric key
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to create biometric cipher")
            null
        }
    }

    /**
     * Unlocks vault using biometric authentication
     */
    fun unlockWithBiometric(cipher: Cipher?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                biometricPrompted = true
            )

            try {
                Timber.d("Attempting biometric unlock")

                val result = sessionPassphraseManager.deriveSessionPassphrase(cipher)

                when (result) {
                    is SessionPassphraseManager.DerivationResult.Success -> {
                        // Complete session unlock
                        sessionManager.completeUnlock(result.sessionKey, result.salt)

                        auditLogger.logAuthenticationEvent(
                            eventType = AuditEventType.LOGIN,
                            username = "BIOMETRIC_USER",
                            outcome = AuditOutcome.SUCCESS
                        )

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isUnlocked = true,
                            errorMessage = null
                        )

                        Timber.d("Biometric unlock successful")
                    }
                    is SessionPassphraseManager.DerivationResult.Error -> {
                        auditLogger.logAuthenticationEvent(
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            username = "BIOMETRIC_USER",
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = result.message
                        )

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Biometric unlock failed: ${result.message}"
                        )

                        Timber.w("Biometric unlock failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Biometric unlock error")

                auditLogger.logAuthenticationEvent(
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    username = "BIOMETRIC_USER",
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.message
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Biometric authentication error: ${e.message}"
                )
            }
        }
    }

    /**
     * Unlocks vault using master password
     */
    fun unlockWithPassword(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                Timber.d("Attempting password unlock")

                val result = sessionPassphraseManager.deriveSessionFromPassword(password)

                when (result) {
                    is SessionPassphraseManager.DerivationResult.Success -> {
                        // Complete session unlock
                        sessionManager.completeUnlock(result.sessionKey, result.salt)

                        auditLogger.logAuthenticationEvent(
                            eventType = AuditEventType.LOGIN,
                            username = "PASSWORD_USER",
                            outcome = AuditOutcome.SUCCESS
                        )

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isUnlocked = true,
                            errorMessage = null
                        )

                        Timber.d("Password unlock successful")
                    }
                    is SessionPassphraseManager.DerivationResult.Error -> {
                        auditLogger.logAuthenticationEvent(
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            username = "PASSWORD_USER",
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = result.message
                        )

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Password unlock failed: ${result.message}"
                        )

                        Timber.w("Password unlock failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Password unlock error")

                auditLogger.logAuthenticationEvent(
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    username = "PASSWORD_USER",
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.message
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Password authentication error: ${e.message}"
                )
            }
        }
    }

    /**
     * Handles biometric authentication errors
     */
    fun handleBiometricError(errorCode: Int, errorMessage: String) {
        Timber.w("Biometric error: $errorCode - $errorMessage")

        val userMessage = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED -> "Biometric authentication was canceled"
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "No biometric credentials enrolled"
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "Biometric hardware not available"
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "Biometric hardware currently unavailable"
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric authentication locked out permanently"
            BiometricPrompt.ERROR_LOCKOUT -> "Too many failed attempts. Try again later."
            else -> "Biometric authentication failed: $errorMessage"
        }

        auditLogger.logAuthenticationEvent(
            eventType = AuditEventType.AUTHENTICATION_FAILURE,
            username = "BIOMETRIC_USER",
            outcome = AuditOutcome.FAILURE,
            errorMessage = "Biometric error $errorCode: $errorMessage"
        )

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = userMessage,
            biometricPrompted = true
        )
    }

    /**
     * Clears error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Reset biometric prompted state
     */
    fun resetBiometricPrompted() {
        _uiState.value = _uiState.value.copy(biometricPrompted = false)
    }
}