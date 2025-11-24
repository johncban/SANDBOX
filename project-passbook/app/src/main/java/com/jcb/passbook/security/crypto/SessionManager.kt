package com.jcb.passbook.security.crypto

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionManager handles ephemeral session keys and manages their lifecycle.
 * Session keys are derived from AMK and wiped on inactivity or app backgrounding.
 *
 * ✅ ENHANCED: Now automatically triggers key rotation on logout
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class SessionManager @Inject constructor(
    private val masterKeyManager: MasterKeyManager,
    private val auditLoggerProvider: () -> AuditLogger,
    private val secureMemoryUtils: SecureMemoryUtils
) : DefaultLifecycleObserver {

    // ✅ ADDED: Lazy initialization of auditLogger
    private val auditLogger: AuditLogger by lazy { auditLoggerProvider() }

    companion object {
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private const val ESK_SIZE_BYTES = 32
        private const val TAG = "SessionManager"
    }

    private var amk: ByteArray? = null
    private var esk: ByteArray? = null
    private var sessionId: String? = null
    private var sessionStartTime: Long = 0
    private var lastActivityTime: Long = 0
    private var timeoutJob: Job? = null
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var isSessionActive = false

    // ✅ NEW: Callback for logout event
    private var onLogoutCallback: (suspend () -> Unit)? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * ✅ NEW: Set callback to be invoked on logout (for key rotation trigger)
     */
    fun setOnLogoutCallback(callback: suspend () -> Unit) {
        onLogoutCallback = callback
    }

    /**
     * Start a new session with biometric authentication
     */
    suspend fun startSession(activity: FragmentActivity): SessionResult {
        var unwrappedAMK: ByteArray? = null
        return try {
            if (isSessionActive) {
                auditLogger.logSecurityEvent(
                    "Attempted to start session while one is already active",
                    "WARNING",
                    AuditOutcome.WARNING
                )
                return SessionResult.AlreadyActive
            }

            unwrappedAMK = masterKeyManager.unwrapAMK(activity)
            if (unwrappedAMK == null) {
                auditLogger.logAuthentication(
                    "SYSTEM",
                    AuditEventType.AUTHENTICATION_FAILURE,
                    AuditOutcome.FAILURE,
                    "Failed to unwrap AMK"
                )
                return SessionResult.AuthenticationFailed
            }

            // Store AMK and derive ESK
            amk = secureMemoryUtils.secureCopy(unwrappedAMK)
            deriveEphemeralSessionKey()

            sessionId = UUID.randomUUID().toString()
            sessionStartTime = System.currentTimeMillis()
            lastActivityTime = sessionStartTime
            isSessionActive = true

            // Start inactivity timeout
            startTimeoutTimer()

            auditLogger.logUserAction(
                null, "SYSTEM", AuditEventType.LOGIN,
                "Session started successfully",
                "SESSION", sessionId,
                AuditOutcome.SUCCESS,
                null, "NORMAL"
            )

            Timber.d("Session started: $sessionId")
            SessionResult.Success(sessionId!!)

        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Failed to start session: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            Timber.e(e, "Failed to start session")
            SessionResult.Error(e.message ?: "Unknown error")
        } finally {
            unwrappedAMK?.let { secureMemoryUtils.secureWipe(it) }
        }
    }

    /**
     * Derive ephemeral session key from AMK
     */
    private fun deriveEphemeralSessionKey() {
        val amkCopy = amk ?: throw IllegalStateException("AMK not available")

        // Simple HKDF-like derivation (in production, use proper HKDF)
        val sessionNonce = secureMemoryUtils.generateSecureRandom(16)
        val combined = amkCopy + sessionNonce + "ESK".toByteArray()

        try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val derived = digest.digest(combined)
            esk = derived.copyOf(ESK_SIZE_BYTES)
        } finally {
            secureMemoryUtils.secureWipe(combined)
            secureMemoryUtils.secureWipe(sessionNonce)
        }
    }

    /**
     * Get the current ESK for encryption operations
     */
    fun getEphemeralSessionKey(): SecretKeySpec? {
        updateLastActivity()
        return esk?.let { SecretKeySpec(it, "AES") }
    }

    /**
     * Get the current AMK for key derivation
     */
    fun getApplicationMasterKey(): ByteArray? {
        updateLastActivity()
        return amk?.let { secureMemoryUtils.secureCopy(it) }
    }

    /**
     * Update last activity time and restart timeout
     */
    fun updateLastActivity() {
        if (isSessionActive) {
            lastActivityTime = System.currentTimeMillis()
            startTimeoutTimer()
        }
    }

    /**
     * Start/restart the inactivity timeout timer
     */
    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = sessionScope.launch {
            delay(SESSION_TIMEOUT_MS)
            if (System.currentTimeMillis() - lastActivityTime >= SESSION_TIMEOUT_MS) {
                endSession("Inactivity timeout")
            }
        }
    }

    /**
     * ✅ ENHANCED: End the current session and wipe keys
     * Now triggers key rotation callback on manual logout
     */
    suspend fun endSession(reason: String = "Manual") {
        if (!isSessionActive) return

        val currentSessionId = sessionId
        val duration = System.currentTimeMillis() - sessionStartTime

        // Cancel timeout timer
        timeoutJob?.cancel()

        // Wipe sensitive data
        amk?.let { secureMemoryUtils.secureWipe(it) }
        esk?.let { secureMemoryUtils.secureWipe(it) }
        amk = null
        esk = null
        isSessionActive = false

        val endedSessionId = sessionId
        sessionId = null

        auditLogger.logUserAction(
            null, "SYSTEM", AuditEventType.LOGOUT,
            "Session ended: $reason (duration: ${duration}ms)",
            "SESSION", endedSessionId,
            AuditOutcome.SUCCESS,
            null, "NORMAL"
        )

        Timber.d("Session ended: $currentSessionId, reason: $reason, duration: ${duration}ms")

        // ✅ NEW: Trigger key rotation callback if this is a manual logout
        if (reason == "Manual" || reason == "User logout") {
            onLogoutCallback?.invoke()
        }
    }

    /**
     * Check if session is currently active
     */
    fun isSessionActive(): Boolean = isSessionActive

    /**
     * Get current session ID
     */
    fun getCurrentSessionId(): String? = sessionId

    /**
     * Get session duration in milliseconds
     */
    fun getSessionDuration(): Long {
        return if (isSessionActive) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            0L
        }
    }

    /**
     * Force session renewal (useful for key rotation)
     */
    suspend fun renewSession(activity: FragmentActivity): SessionResult {
        endSession("Session renewal")
        return startSession(activity)
    }

    // Lifecycle observers
    override fun onStop(owner: LifecycleOwner) {
        sessionScope.launch {
            endSession("App backgrounded")
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        sessionScope.launch {
            endSession("App destroyed")
        }
        sessionScope.cancel()
    }

    /**
     * Session operation results
     */
    sealed class SessionResult {
        data class Success(val sessionId: String) : SessionResult()
        object AuthenticationFailed : SessionResult()
        object AlreadyActive : SessionResult()
        data class Error(val message: String) : SessionResult()
    }
}
