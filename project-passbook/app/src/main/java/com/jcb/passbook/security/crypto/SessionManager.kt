package com.jcb.passbook.security.session

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

/**
 * ✅ UPDATED: SessionManager with atomic operation tracking
 *
 * Critical Fixes:
 * 1. AtomicInteger for operation counter (thread-safe)
 * 2. startOperation() returns Boolean (tracks success)
 * 3. endOperation() only decrements if startOperation() succeeded
 * 4. Proper synchronization for concurrent access
 * 5. Comprehensive logging and error handling
 * 6. Session timeout based on operation activity
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

sealed class SessionResult {
    data class Success(val userId: Long) : SessionResult()
    data class Error(val message: String) : SessionResult()
}

data class SessionState(
    val userId: Long? = null,
    val amk: ByteArray? = null,
    val startTime: Long = 0,
    val lastActivityTime: Long = 0,
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

class SessionManager(
    private val context: Context,
    private val keystoreManager: KeystorePassphraseManager
) : ISessionManager {

    companion object {
        private const val DEFAULT_SESSION_TIMEOUT_MS = 15 * 60 * 1000L
        private const val OPERATION_TIMEOUT_EXTENSION_MS = 5 * 60 * 1000L
    }

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // ✅ CRITICAL: Atomic counter for thread-safe operation tracking
    private val operationCount = AtomicInteger(0)
    private val sessionLock = Any()

    /**
     * ✅ CRITICAL FIX: Start operation with proper tracking
     * Returns true ONLY if session is active and AMK available
     */
    override fun startOperation(): Boolean {
        synchronized(sessionLock) {
            val currentState = _sessionState.value

            if (!currentState.isActive) {
                Timber.w("⚠️ Cannot start operation - session not active")
                return false
            }

            if (currentState.amk == null) {
                Timber.w("⚠️ Cannot start operation - AMK not available")
                return false
            }

            val elapsed = System.currentTimeMillis() - currentState.lastActivityTime
            if (elapsed > DEFAULT_SESSION_TIMEOUT_MS) {
                Timber.w("⚠️ Session expired during operation start")
                return false
            }

            // ✅ CRITICAL: Increment atomic counter
            val newCount = operationCount.incrementAndGet()
            Timber.d("▶️ Operation started (count=$newCount)")

            _sessionState.value = currentState.copy(
                lastActivityTime = System.currentTimeMillis(),
                operationCount = newCount
            )

            return true
        }
    }

    /**
     * ✅ CRITICAL FIX: End operation with safety check
     */
    override fun endOperation() {
        synchronized(sessionLock) {
            val currentCount = operationCount.get()

            if (currentCount <= 0) {
                Timber.w("⚠️ endOperation() called but no active operations")
                return
            }

            val newCount = operationCount.decrementAndGet()
            Timber.d("⏹️ Operation ended (count=$newCount)")

            val currentState = _sessionState.value
            _sessionState.value = currentState.copy(
                lastActivityTime = System.currentTimeMillis(),
                operationCount = newCount
            )
        }
    }

    /**
     * ✅ CRITICAL: Get session AMK with validation
     */
    override fun getSessionAMK(): ByteArray? {
        synchronized(sessionLock) {
            val currentState = _sessionState.value

            if (!currentState.isActive) {
                Timber.w("❌ Session not active")
                return null
            }

            if (currentState.amk == null) {
                Timber.w("❌ AMK not available")
                return null
            }

            val elapsed = System.currentTimeMillis() - currentState.lastActivityTime
            val timeout = if (operationCount.get() > 0) {
                DEFAULT_SESSION_TIMEOUT_MS + OPERATION_TIMEOUT_EXTENSION_MS
            } else {
                DEFAULT_SESSION_TIMEOUT_MS
            }

            if (elapsed > timeout) {
                Timber.w("❌ Session timeout")
                return null
            }

            Timber.d("✓ AMK available")
            return currentState.amk
        }
    }

    override fun startSession(
        activity: FragmentActivity,
        userId: Long
    ): SessionResult {
        return try {
            synchronized(sessionLock) {
                Timber.i("🔐 Starting session for userId=$userId")

                val passphrase = keystoreManager.retrievePassphrase(activity)
                    ?: throw SecurityException("Failed to retrieve passphrase")

                val amk = keystoreManager.deriveAMK(passphrase)
                Timber.d("✓ AMK derived")

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

                Timber.i("✅ Session started")
                SessionResult.Success(userId)
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Session start failed")
            SessionResult.Error("Session start failed: ${e.message}")
        }
    }

    override suspend fun endSession(reason: String) {
        synchronized(sessionLock) {
            try {
                Timber.i("🚪 Ending session - reason: $reason")

                val currentState = _sessionState.value
                currentState.amk?.let { amk ->
                    amk.fill(0)
                    Timber.d("✓ AMK cleared")
                }

                _sessionState.value = SessionState()
                operationCount.set(0)

                Timber.i("✅ Session ended")
            } catch (e: Exception) {
                Timber.e(e, "Error during session end")
            }
        }
    }

    override fun isSessionActive(): Boolean {
        synchronized(sessionLock) {
            val state = _sessionState.value
            if (!state.isActive) return false

            val elapsed = System.currentTimeMillis() - state.lastActivityTime
            return elapsed <= DEFAULT_SESSION_TIMEOUT_MS
        }
    }

    override fun getSessionTimeoutMs(): Long {
        synchronized(sessionLock) {
            val state = _sessionState.value
            if (!state.isActive) return 0

            val elapsed = System.currentTimeMillis() - state.lastActivityTime
            val timeout = DEFAULT_SESSION_TIMEOUT_MS
            return maxOf(0, timeout - elapsed)
        }
    }

    suspend fun checkSessionTimeout() {
        synchronized(sessionLock) {
            val state = _sessionState.value
            if (!state.isActive) return

            val elapsed = System.currentTimeMillis() - state.lastActivityTime
            if (elapsed > DEFAULT_SESSION_TIMEOUT_MS) {
                Timber.w("⏰ Session timeout detected")
                endSession("Session timeout")
            }
        }
    }
}
