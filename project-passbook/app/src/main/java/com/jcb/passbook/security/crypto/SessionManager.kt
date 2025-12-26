package com.jcb.passbook.security.crypto

import android.content.Context
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Session management for the Application Master Key (AMK).
 *
 * Responsibilities:
 * - Derive and hold an in‑memory AMK for the active user
 * - Track active operations that depend on AMK
 * - Enforce session timeout and invalidate AMK securely
 */
interface ISessionManager {
    fun startOperation(): Boolean
    fun endOperation()
    fun getSessionAMK(): ByteArray?

    fun startSession(activity: FragmentActivity, userId: Long): SessionResult
    suspend fun endSession(reason: String)

    fun isSessionActive(): Boolean
    fun getSessionTimeoutMs(): Long
}

/**
 * Result type for session start.
 */
sealed class SessionResult {
    data class Success(val userId: Long) : SessionResult()
    data class Error(val message: String) : SessionResult()
}

/**
 * Immutable snapshot of the current session state.
 */
data class SessionState(
    val userId: Long? = null,
    val amk: ByteArray? = null,
    val startTime: Long = 0L,
    val lastActivityTime: Long = 0L,
    val operationCount: Int = 0,
    val isActive: Boolean = false
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

/**
 * Thread‑safe session manager with atomic operation tracking.
 *
 * Important invariants:
 * - When isActive == false, amk MUST be null and operationCount == 0
 * - operationCount never goes below 0
 * - AMK bytes are zeroed on endSession()
 */
class SessionManager(
    @Suppress("unused") // kept for future use if needed
    private val context: Context,
    private val keystoreManager: KeystorePassphraseManager
) : ISessionManager {

    companion object {
        // Base session timeout
        private const val DEFAULT_SESSION_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes
        // Extra time while there are active operations
        private const val OPERATION_TIMEOUT_EXTENSION_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // Atomic counter for operations that rely on AMK
    private val operationCount = AtomicInteger(0)

    // Single lock for all state mutations
    private val sessionLock = Any()

    /**
     * Start an operation that depends on AMK.
     *
     * Returns true ONLY if:
     * - session is active
     * - AMK is available
     * - session is not already expired
     */
    override fun startOperation(): Boolean {
        synchronized(sessionLock) {
            val current = _sessionState.value

            if (!current.isActive) {
                Timber.w("SessionManager: Cannot start operation – session not active")
                return false
            }

            if (current.amk == null) {
                Timber.w("SessionManager: Cannot start operation – AMK not available")
                return false
            }

            val now = System.currentTimeMillis()
            val elapsed = now - current.lastActivityTime
            if (elapsed > DEFAULT_SESSION_TIMEOUT_MS) {
                Timber.w("SessionManager: Cannot start operation – session already expired")
                return false
            }

            val newCount = operationCount.incrementAndGet()
            Timber.d("SessionManager: Operation started (count=$newCount)")

            _sessionState.value = current.copy(
                lastActivityTime = now,
                operationCount = newCount
            )

            return true
        }
    }

    /**
     * End a previously started operation.
     *
     * Does nothing if no operations are active.
     */
    override fun endOperation() {
        synchronized(sessionLock) {
            val current = _sessionState.value
            val currentCount = operationCount.get()

            if (currentCount <= 0) {
                Timber.w("SessionManager: endOperation() called with no active operations")
                return
            }

            val newCount = operationCount.decrementAndGet()
            Timber.d("SessionManager: Operation ended (count=$newCount)")

            _sessionState.value = current.copy(
                lastActivityTime = System.currentTimeMillis(),
                operationCount = newCount
            )
        }
    }

    /**
     * Return the AMK only if:
     * - session is active
     * - AMK exists
     * - session has not timed out (with extension while operations active)
     */
    override fun getSessionAMK(): ByteArray? {
        synchronized(sessionLock) {
            val current = _sessionState.value

            if (!current.isActive) {
                Timber.w("SessionManager: getSessionAMK – session not active")
                return null
            }

            val amk = current.amk
            if (amk == null) {
                Timber.w("SessionManager: getSessionAMK – AMK not available")
                return null
            }

            val now = System.currentTimeMillis()
            val elapsed = now - current.lastActivityTime

            val timeout = if (operationCount.get() > 0) {
                DEFAULT_SESSION_TIMEOUT_MS + OPERATION_TIMEOUT_EXTENSION_MS
            } else {
                DEFAULT_SESSION_TIMEOUT_MS
            }

            if (elapsed > timeout) {
                Timber.w("SessionManager: getSessionAMK – session timeout (elapsed=$elapsed, timeout=$timeout)")
                return null
            }

            Timber.d("SessionManager: AMK available")
            return amk
        }
    }

    /**
     * Start a new session for the given user.
     *
     * This will:
     * - retrieve the user's passphrase via KeystorePassphraseManager
     * - derive the in-memory AMK
     * - reset operation count
     */
    override fun startSession(
        activity: FragmentActivity,
        userId: Long
    ): SessionResult {
        return try {
            synchronized(sessionLock) {
                Timber.i("SessionManager: Starting session for userId=$userId")

                val passphrase = keystoreManager.retrievePassphrase(activity)
                    ?: throw SecurityException("Failed to retrieve passphrase")

                val amk = keystoreManager.deriveAMK(passphrase)
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
     * End the current session and securely clear AMK.
     */
    override suspend fun endSession(reason: String) {
        synchronized(sessionLock) {
            try {
                Timber.i("SessionManager: Ending session – reason: $reason")

                val current = _sessionState.value

                current.amk?.let { amk ->
                    amk.fill(0)
                    Timber.d("SessionManager: AMK cleared from memory")
                }

                _sessionState.value = SessionState()
                operationCount.set(0)

                Timber.i("SessionManager: Session ended")
            } catch (e: Exception) {
                Timber.e(e, "SessionManager: Error during session end")
            }
        }
    }

    /**
     * Simple check if session is active and within base timeout.
     */
    override fun isSessionActive(): Boolean {
        synchronized(sessionLock) {
            val state = _sessionState.value
            if (!state.isActive) return false

            val now = System.currentTimeMillis()
            val elapsed = now - state.lastActivityTime

            return elapsed <= DEFAULT_SESSION_TIMEOUT_MS
        }
    }

    /**
     * Remaining time before base timeout (ignores operation extension).
     */
    override fun getSessionTimeoutMs(): Long {
        synchronized(sessionLock) {
            val state = _sessionState.value
            if (!state.isActive) return 0L

            val now = System.currentTimeMillis()
            val elapsed = now - state.lastActivityTime
            val remaining = DEFAULT_SESSION_TIMEOUT_MS - elapsed

            return if (remaining > 0) remaining else 0L
        }
    }

    /**
     * Optional helper to be called from a periodic job/timer.
     * If base timeout has elapsed, the session is ended.
     */
    suspend fun checkSessionTimeout() {
        synchronized(sessionLock) {
            val state = _sessionState.value
            if (!state.isActive) return

            val now = System.currentTimeMillis()
            val elapsed = now - state.lastActivityTime

            if (elapsed > DEFAULT_SESSION_TIMEOUT_MS) {
                Timber.w("SessionManager: Session timeout detected in checkSessionTimeout (elapsed=$elapsed)")
                // Release lock before calling suspend function to avoid deadlock
            }
        }

        // Call endSession outside synchronized block
        if (!isSessionActive()) {
            endSession("Session timeout")
        }
    }
}
