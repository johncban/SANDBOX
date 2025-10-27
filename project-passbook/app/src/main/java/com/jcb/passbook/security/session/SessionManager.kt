package com.jcb.passbook.security.session

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the ephemeral session state for vault access
 * Session keys are never persisted and must be re-derived on cold start
 */
@Singleton
class SessionManager @Inject constructor(
    private val context: Context
) {
    enum class SessionState {
        LOCKED,      // No access - require unlock
        UNLOCKING,   // In progress - biometric/password prompt
        UNLOCKED     // Active session - vault accessible
    }

    enum class LockTrigger {
        APP_START,
        PROCESS_DEATH,
        BACKGROUND_TIMEOUT,
        MANUAL_LOCK,
        SECURITY_EVENT,
        BIOMETRIC_LOCKOUT
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val BACKGROUND_TIMEOUT_MS = 30_000L // 30 seconds
    }

    private val _sessionState = MutableStateFlow(SessionState.LOCKED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _lockTrigger = MutableStateFlow<LockTrigger?>(null)
    val lockTrigger: StateFlow<LockTrigger?> = _lockTrigger.asStateFlow()

    // Ephemeral session data - never persisted
    @Volatile
    private var sessionKey: ByteArray? = null
    
    @Volatile
    private var sessionSalt: ByteArray? = null
    
    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var backgroundTime: Long = 0

    @Volatile
    private var isSecureWindowActive = false

    /**
     * Starts an unlock attempt - sets state to UNLOCKING
     */
    fun startUnlock() {
        Log.d(TAG, "Starting unlock process")
        _sessionState.value = SessionState.UNLOCKING
        _lockTrigger.value = null
    }

    /**
     * Completes unlock with derived session material
     * @param derivedKey The ephemeral session key from biometric/password derivation
     * @param salt The per-session salt used in derivation
     */
    fun completeUnlock(derivedKey: ByteArray, salt: ByteArray) {
        Log.d(TAG, "Completing unlock with ephemeral session key")
        
        // Clear any existing session first
        clearSessionMaterial()
        
        // Set new ephemeral session data
        sessionKey = derivedKey.copyOf()
        sessionSalt = salt.copyOf()
        sessionId = UUID.randomUUID().toString()
        
        _sessionState.value = SessionState.UNLOCKED
        _lockTrigger.value = null
        
        Log.d(TAG, "Session unlocked with ID: $sessionId")
    }

    /**
     * Locks the session and clears all ephemeral data
     */
    fun lock(trigger: LockTrigger) {
        Log.d(TAG, "Locking session due to: $trigger")
        
        clearSessionMaterial()
        _sessionState.value = SessionState.LOCKED
        _lockTrigger.value = trigger
    }

    /**
     * Records when app goes to background for timeout calculation
     */
    fun onAppBackground() {
        backgroundTime = System.currentTimeMillis()
        Log.d(TAG, "App backgrounded at: $backgroundTime")
    }

    /**
     * Checks if background timeout exceeded when app resumes
     */
    fun onAppForeground(): Boolean {
        val currentTime = System.currentTimeMillis()
        val backgroundDuration = currentTime - backgroundTime
        
        if (backgroundTime > 0 && backgroundDuration > BACKGROUND_TIMEOUT_MS && isUnlocked()) {
            Log.w(TAG, "Background timeout exceeded: ${backgroundDuration}ms")
            lock(LockTrigger.BACKGROUND_TIMEOUT)
            return false
        }
        
        Log.d(TAG, "App foregrounded, background duration: ${backgroundDuration}ms")
        backgroundTime = 0
        return true
    }

    /**
     * Gets the current session passphrase for database access
     * Returns null if session is locked
     */
    fun getSessionPassphrase(): CharArray? {
        return if (isUnlocked() && sessionKey != null) {
            // Convert session key to char array for SQLCipher
            android.util.Base64.encodeToString(sessionKey, android.util.Base64.NO_WRAP).toCharArray()
        } else {
            null
        }
    }

    /**
     * Gets current session ID for audit logging
     */
    fun getCurrentSessionId(): String? = sessionId

    /**
     * Checks if session is currently unlocked
     */
    fun isUnlocked(): Boolean = _sessionState.value == SessionState.UNLOCKED

    /**
     * Checks if session is currently locked
     */
    fun isLocked(): Boolean = _sessionState.value == SessionState.LOCKED

    /**
     * Enables secure window flag (prevents screenshots)
     */
    fun enableSecureWindow() {
        isSecureWindowActive = true
    }

    /**
     * Disables secure window flag
     */
    fun disableSecureWindow() {
        isSecureWindowActive = false
    }

    /**
     * Checks if secure window should be active
     */
    fun shouldUseSecureWindow(): Boolean = isSecureWindowActive && isUnlocked()

    /**
     * Clears all ephemeral session material
     */
    private fun clearSessionMaterial() {
        sessionKey?.let { key ->
            // Zero out the key material
            key.fill(0)
        }
        sessionKey = null
        
        sessionSalt?.let { salt ->
            salt.fill(0)
        }
        sessionSalt = null
        
        sessionId = null
        isSecureWindowActive = false
        
        Log.d(TAG, "Session material cleared")
    }

    /**
     * Force immediate lock for security events
     */
    fun emergencyLock() {
        Log.w(TAG, "Emergency lock triggered")
        lock(LockTrigger.SECURITY_EVENT)
    }
}
