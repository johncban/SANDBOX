package com.jcb.passbook.security.crypto

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

/**
 * ✅ REFACTORED: SessionManager with complete ephemeral key support
 * 
 * Manages:
 * - Session lifecycle (start, end, timeout)
 * - AMK (Application Master Key) storage and lifecycle
 * - Ephemeral session key for audit encryption
 * - Session metadata and state tracking
 */
class SessionManager(
    @Suppress("unused") private val context: Context,
    private val keystoreManager: KeystorePassphraseManager,
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val TAG = "SessionManager"
        private const val SESSION_TIMEOUT_MILLIS = 15 * 60 * 1000L  // 15 minutes
        private const val SESSION_EXTENSION_MILLIS = 5 * 60 * 1000L   // 5 minute extension
    }

    private val sessionLock = Any()
    private val operationCount = AtomicInteger(0)

    // Session state observable
    private val _sessionState = MutableLiveData<SessionState>(
        SessionState(
            userId = null,
            amk = null,
            startTime = 0L,
            lastActivityTime = 0L,
            operationCount = 0,
            isActive = false
        )
    )

    val sessionState = _sessionState

    /**
     * Start a new session for the given user
     * 
     * @param activity FragmentActivity for biometric/PIN prompt (optional)
     * @param userId The user ID
     * @return SessionResult.Success or SessionResult.Error
     */
    fun startSession(
        activity: FragmentActivity,
        userId: Long
    ): SessionResult {
        return try {
            synchronized(sessionLock) {
                Timber.i("SessionManager: Starting session for userId=$userId")

                // Retrieve passphrase from keystore
                val passphrase = keystoreManager.retrievePassphrase(activity)
                    ?: throw SecurityException("Failed to retrieve passphrase")

                // Derive AMK from passphrase
                val amk = keystoreManager.deriveAMK(passphrase)
                passphrase.fill(0)  // Clear from memory

                Timber.d("SessionManager: AMK derived")

                val now = System.currentTimeMillis()
                _sessionState.value = SessionState(
                    userId = userId,
                    amk = amk,
                    startTime = now,
                    lastActivityTime = now,
                    operationCount = 0,
                    isActive = true
                )

                operationCount.set(0)
                Timber.i("SessionManager: Session started successfully")
                SessionResult.Success(userId)
            }
        } catch (e: Exception) {
            Timber.e(e, "SessionManager: Session start failed")
            SessionResult.Error("Session start failed: ${e.message}")
        }
    }

    /**
     * End current session (logout)
     */
    fun endSession(reason: String = "Session ended") {
        synchronized(sessionLock) {
            val state = _sessionState.value ?: return
            
            if (state.amk != null) {
                state.amk.fill(0)  // Clear AMK from memory
            }

            _sessionState.value = SessionState(
                userId = null,
                amk = null,
                startTime = 0L,
                lastActivityTime = 0L,
                operationCount = 0,
                isActive = false
            )

            Timber.i("SessionManager: Session ended - $reason")
        }
    }

    /**
     * Check if session is still active
     */
    fun isSessionActive(): Boolean {
        synchronized(sessionLock) {
            val state = _sessionState.value ?: return false
            
            if (!state.isActive) return false
            
            // Check timeout
            val elapsed = System.currentTimeMillis() - state.lastActivityTime
            if (elapsed > SESSION_TIMEOUT_MILLIS) {
                Timber.w("Session timeout after ${elapsed}ms")
                endSession("Session timeout")
                return false
            }

            return true
        }
    }

    /**
     * Get current session AMK (if session active)
     */
    fun getSessionAMK(): ByteArray? {
        synchronized(sessionLock) {
            val state = _sessionState.value ?: return null
            return if (isSessionActive()) state.amk else null
        }
    }

    /**
     * Start operation (updates lastActivityTime)
     */
    fun startOperation() {
        synchronized(sessionLock) {
            val state = _sessionState.value ?: return
            if (!state.isActive) return

            _sessionState.value = state.copy(
                lastActivityTime = System.currentTimeMillis(),
                operationCount = operationCount.incrementAndGet()
            )
        }
    }

    /**
     * End operation (may extend session)
     */
    fun endOperation() {
        synchronized(sessionLock) {
            val state = _sessionState.value ?: return
            if (!state.isActive) return

            _sessionState.value = state.copy(
                lastActivityTime = System.currentTimeMillis()
            )
        }
    }

    /**
     * Check and potentially extend session on activity
     */
    fun checkSessionTimeout() {
        synchronized(sessionLock) {
            val state = _sessionState.value ?: return
            if (!state.isActive) return

            val elapsed = System.currentTimeMillis() - state.lastActivityTime
            val remaining = SESSION_TIMEOUT_MILLIS - elapsed

            if (remaining < SESSION_EXTENSION_MILLIS && operationCount.get() > 0) {
                // Extend session
                _sessionState.value = state.copy(
                    lastActivityTime = System.currentTimeMillis()
                )
                Timber.d("Session extended due to active operations")
            }
        }
    }

    // ========== Ephemeral Key Support for Audit ==========

    /**
     * Get ephemeral session key for audit encryption
     * Creates a derived key from AMK valid only for current session
     * 
     * ✅ FIXED: Now properly implemented
     */
    fun getEphemeralSessionKey(): SecretKeySpec? {
        synchronized(sessionLock) {
            val amk = getSessionAMK() ?: return null

            return try {
                // Derive ephemeral key from AMK using KDF
                // Simple KDF: HMAC-SHA256(AMK, "EPHEMERAL_KEY")
                val hmac = Mac.getInstance("HmacSHA256")
                val keySpec = SecretKeySpec(
                    amk, 0, amk.size, "HmacSHA256"
                )
                hmac.init(keySpec)

                val derived = hmac.doFinal("EPHEMERAL_KEY".toByteArray())

                // Truncate to 32 bytes for AES-256
                val keyMaterial = derived.copyOfRange(0, 32)
                SecretKeySpec(keyMaterial, 0, 32, "AES")
            } catch (e: Exception) {
                Timber.e(e, "Failed to derive ephemeral key")
                null
            }
        }
    }

    /**
     * Verify ephemeral key is still valid
     */
    fun isEphemeralKeyValid(): Boolean {
        synchronized(sessionLock) {
            return getSessionAMK() != null && isSessionActive()
        }
    }

    /**
     * Get encrypted session metadata for audit trail
     */
    fun getSessionMetadata(): SessionMetadata {
        synchronized(sessionLock) {
            val state = _sessionState.value
            return SessionMetadata(
                userId = state?.userId ?: 0L,
                sessionStartTime = state?.startTime ?: 0L,
                sessionDuration = if (state != null) {
                    System.currentTimeMillis() - state.startTime
                } else {
                    0L
                },
                operationCount = state?.operationCount ?: 0,
                isActive = state?.isActive ?: false
            )
        }
    }

    // ========== Data Classes ==========

    data class SessionState(
        val userId: Long?,
        val amk: ByteArray?,
        val startTime: Long,
        val lastActivityTime: Long,
        val operationCount: Int,
        val isActive: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SessionState

            if (userId != other.userId) return false
            if (amk != null) {
                if (other.amk == null) return false
                if (!amk.contentEquals(other.amk)) return false
            } else if (other.amk != null) return false
            if (startTime != other.startTime) return false
            if (lastActivityTime != other.lastActivityTime) return false
            if (operationCount != other.operationCount) return false
            if (isActive != other.isActive) return false

            return true
        }

        override fun hashCode(): Int {
            var result = userId?.hashCode() ?: 0
            result = 31 * result + (amk?.contentHashCode() ?: 0)
            result = 31 * result + startTime.hashCode()
            result = 31 * result + lastActivityTime.hashCode()
            result = 31 * result + operationCount
            result = 31 * result + isActive.hashCode()
            return result
        }
    }

    data class SessionMetadata(
        val userId: Long,
        val sessionStartTime: Long,
        val sessionDuration: Long,
        val operationCount: Int,
        val isActive: Boolean
    )

    sealed class SessionResult {
        data class Success(val userId: Long) : SessionResult()
        data class Error(val message: String) : SessionResult()
    }
}

// Extension to support interface if one is defined
interface ISessionManager {
    fun startSession(
        activity: FragmentActivity,
        userId: Long
    ): SessionManager.SessionResult

    fun endSession(reason: String = "Session ended")
    fun isSessionActive(): Boolean
    fun getSessionAMK(): ByteArray?
    fun startOperation()
    fun endOperation()
    fun checkSessionTimeout()
    fun getEphemeralSessionKey(): SecretKeySpec?
    fun isEphemeralKeyValid(): Boolean
    fun getSessionMetadata(): SessionManager.SessionMetadata
}
