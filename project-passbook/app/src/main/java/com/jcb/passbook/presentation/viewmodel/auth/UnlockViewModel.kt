package com.jcb.passbook.presentation.viewmodel.auth

import android.content.Context
import android.util.Log
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
        
        // Log unlock attempt
        auditLogger.logAuthentication(
            username = "SYSTEM",
            eventType = AuditEventType.LOGIN,
            outcome = AuditOutcome.WARNING,
            errorMessage = "Vault unlock attempt started"
        )
    }

    private fun checkBiometricAvailability() {
        val availability = BiometricHelper.checkAvailability(context)
        val biometricAvailable = availability == BiometricHelper.Availability.AVAILABLE
        
        _uiState.value = _uiState.value.copy(
            biometricAvailable = biometricAvailable
        )
        
        Log.d(TAG, "Biometric availability: $availability")
    }

    private fun initializeMasterSeed() {
        viewModelScope.launch {
            try {
                if (!sessionPassphraseManager.isMasterSeedInitialized()) {
                    Log.d(TAG, "Initializing master seed")
                    sessionPassphraseManager.initializeMasterSeed()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize master seed", e)
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
            Log.e(TAG, "Failed to create biometric cipher", e)
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
                Log.d(TAG, "Attempting biometric unlock")
                
                val result = sessionPassphraseManager.deriveSessionPassphrase(cipher)
                
                when (result) {
                    is SessionPassphraseManager.DerivationResult.Success -> {
                        // Complete session unlock
                        sessionManager.completeUnlock(result.sessionKey, result.salt)
                        
                        auditLogger.logAuthentication(
                            username = "BIOMETRIC_USER",
                            eventType = AuditEventType.LOGIN,
                            outcome = AuditOutcome.SUCCESS
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isUnlocked = true,
                            errorMessage = null
                        )
                        
                        Log.d(TAG, "Biometric unlock successful")
                    }
                    is SessionPassphraseManager.DerivationResult.Error -> {
                        auditLogger.logAuthentication(
                            username = "BIOMETRIC_USER",
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = result.message
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Biometric unlock failed: ${result.message}"
                        )
                        
                        Log.w(TAG, "Biometric unlock failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Biometric unlock error", e)
                
                auditLogger.logAuthentication(
                    username = "BIOMETRIC_USER",
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
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
                Log.d(TAG, "Attempting password unlock")
                
                val result = sessionPassphraseManager.deriveSessionFromPassword(password)
                
                when (result) {
                    is SessionPassphraseManager.DerivationResult.Success -> {
                        // Complete session unlock
                        sessionManager.completeUnlock(result.sessionKey, result.salt)
                        
                        auditLogger.logAuthentication(
                            username = "PASSWORD_USER",
                            eventType = AuditEventType.LOGIN,
                            outcome = AuditOutcome.SUCCESS
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isUnlocked = true,
                            errorMessage = null
                        )
                        
                        Log.d(TAG, "Password unlock successful")
                    }
                    is SessionPassphraseManager.DerivationResult.Error -> {
                        auditLogger.logAuthentication(
                            username = "PASSWORD_USER",
                            eventType = AuditEventType.AUTHENTICATION_FAILURE,
                            outcome = AuditOutcome.FAILURE,
                            errorMessage = result.message
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Password unlock failed: ${result.message}"
                        )
                        
                        Log.w(TAG, "Password unlock failed: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Password unlock error", e)
                
                auditLogger.logAuthentication(
                    username = "PASSWORD_USER",
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
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
        Log.w(TAG, "Biometric error: $errorCode - $errorMessage")
        
        val userMessage = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED -> "Biometric authentication was canceled"
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "No biometric credentials enrolled"
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "Biometric hardware not available"
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "Biometric hardware currently unavailable"
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric authentication locked out permanently"
            BiometricPrompt.ERROR_LOCKOUT -> "Too many failed attempts. Try again later."
            else -> "Biometric authentication failed: $errorMessage"
        }
        
        auditLogger.logAuthentication(
            username = "BIOMETRIC_USER",
            eventType = AuditEventType.AUTHENTICATION_FAILURE,
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
