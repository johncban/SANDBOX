package com.jcb.passbook.security.session

import android.content.Context
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Inactive)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var sessionAMK: ByteArray? = null
    private var sessionStartTime: Long = 0
    private val isOperationInProgress = AtomicBoolean(false) // ✅ NEW

    companion object {
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private const val OPERATION_GRACE_PERIOD_MS = 30 * 1000L // ✅ 30 seconds grace
    }

    /**
     * ✅ FIXED: Start active operation - extends session timeout
     */
    fun startOperation() {
        isOperationInProgress.set(true)
        Timber.d("✅ Operation started - session timeout extended")
    }

    /**
     * ✅ FIXED: End active operation
     */
    fun endOperation() {
        isOperationInProgress.set(false)
        Timber.d("✅ Operation completed")
    }

    /**
     * Start secure session with AMK
     */
    suspend fun startSession(amk: ByteArray, username: String): Boolean {
        return try {
            if (_sessionState.value is SessionState.Active) {
                Timber.w("Session already active")
                return true
            }

            sessionAMK = amk.copyOf()
            sessionStartTime = System.currentTimeMillis()
            _sessionState.value = SessionState.Active(username)

            auditLogger.logAuthentication(
                username,
                AuditEventType.LOGIN,
                AuditOutcome.SUCCESS
            )

            Timber.i("✅ Session started for user: $username")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to start session")
            auditLogger.logAuthentication(
                username,
                AuditEventType.LOGIN,
                AuditOutcome.FAILURE,
                e.message
            )
            false
        }
    }

    /**
     * ✅ FIXED: Get AMK with operation-aware timeout check
     */
    fun getSessionAMK(): ByteArray? {
        // Check if session is still valid with grace period for active operations
        if (!isSessionValid()) {
            Timber.w("❌ Session expired or inactive")
            endSession()
            return null
        }

        return sessionAMK?.copyOf()
    }

    /**
     * ✅ FIXED: Check session validity with operation grace period
     */
    fun isSessionValid(): Boolean {
        val currentState = _sessionState.value
        if (currentState !is SessionState.Active) {
            return false
        }

        val elapsed = System.currentTimeMillis() - sessionStartTime
        val timeout = if (isOperationInProgress.get()) {
            SESSION_TIMEOUT_MS + OPERATION_GRACE_PERIOD_MS // ✅ Extended timeout
        } else {
            SESSION_TIMEOUT_MS
        }

        return elapsed < timeout
    }

    /**
     * Refresh session timestamp
     */
    fun refreshSession() {
        if (_sessionState.value is SessionState.Active) {
            sessionStartTime = System.currentTimeMillis()
            Timber.d("Session refreshed")
        }
    }

    /**
     * End secure session and wipe secrets
     */
    fun endSession() {
        try {
            val currentState = _sessionState.value
            if (currentState is SessionState.Active) {
                auditLogger.logAuthentication(
                    currentState.username,
                    AuditEventType.LOGOUT,
                    AuditOutcome.SUCCESS
                )
            }

            sessionAMK?.let { secureMemoryUtils.secureWipe(it) }
            sessionAMK = null
            sessionStartTime = 0
            isOperationInProgress.set(false) // ✅ Reset operation flag
            _sessionState.value = SessionState.Inactive

            Timber.i("Session ended, secrets wiped")
        } catch (e: Exception) {
            Timber.e(e, "Error ending session")
        }
    }
}

sealed class SessionState {
    object Inactive : SessionState()
    data class Active(val username: String) : SessionState()
}
