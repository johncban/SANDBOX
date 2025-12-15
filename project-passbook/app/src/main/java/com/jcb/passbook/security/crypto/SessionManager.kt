package com.jcb.passbook.security.crypto

import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionManager"

/**
 * SessionManager - Manages user session state and lifecycle
 *
 * ✅ CRITICAL FIX: Removed masterKeyManager.clearKeys() call that was deleting database keys
 * ✅ CRITICAL FIX: Now only clears in-memory sensitive data, NOT KeyStore entries
 *
 * Core responsibilities:
 * 1. Track session active/inactive state
 * 2. Handle automatic timeout after 15 minutes of inactivity
 * 3. Clear in-memory sensitive data securely on session end
 * 4. Coordinate with UI for logout navigation
 *
 * Security Architecture:
 * - Session-specific data (unwrapped AMK) → Cleared on logout
 * - KeyStore entries (master keys, database keys) → NEVER deleted on logout
 * - User data (database) → Persists across sessions
 *
 * Thread-safe: Uses StateFlow for state management
 */
@Singleton
class SessionManager @Inject constructor(
    private val masterKeyManager: MasterKeyManager,
    private val auditLoggerProvider: () -> AuditLogger,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    // ========== STATE MANAGEMENT ==========

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private var lastActivityTime: Long = 0L

    // Session timeout: 15 minutes
    private val sessionTimeoutMillis = 15 * 60 * 1000L

    // Callback for automatic logout navigation
    private var onLogoutCallback: (() -> Unit)? = null

    // ========== PUBLIC API ==========

    /**
     * Start a new user session
     * Called after successful login or registration
     */
    fun startSession() {
        _isSessionActive.value = true
        lastActivityTime = System.currentTimeMillis()

        Timber.tag(TAG).i("✅ Session started")

        auditLoggerProvider().logSecurityEvent(
            "User session started",
            "INFO",
            AuditOutcome.SUCCESS
        )
    }

    /**
     * ✅ CRITICAL FIX: End session WITHOUT deleting KeyStore entries
     *
     * Previous Bug: Called masterKeyManager.clearKeys() which deleted:
     * - MASTER_WRAP_KEY_ALIAS
     * - DATABASE_KEY_WRAPPER_ALIAS (indirectly)
     * This caused database encryption key to become inaccessible
     *
     * New Behavior: Only clears in-memory cached data
     *
     * @param reason Reason for ending session (e.g., "Manual logout", "Idle timeout")
     */
    suspend fun endSession(reason: String = "Manual logout") {
        if (!_isSessionActive.value) {
            Timber.tag(TAG).d("Session already inactive")
            return
        }

        Timber.tag(TAG).i("Ending session: $reason")

        try {
            // ✅ CRITICAL FIX: Clear ONLY in-memory data, NOT KeyStore entries
            try {
                // Clear cached AMK from memory (not from KeyStore!)
                masterKeyManager.clearInMemorySensitiveData()
                Timber.tag(TAG).d("✅ In-memory encryption keys cleared")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error clearing in-memory keys (non-fatal)")
            }

            // ✅ Wipe other sensitive memory regions
            try {
                secureMemoryUtils.clearSensitiveData()
                Timber.tag(TAG).d("✅ Sensitive data wiped from memory")
            } catch (e: NoSuchMethodError) {
                Timber.tag(TAG).d("clearSensitiveData() not available - skipping")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error clearing sensitive data (non-fatal)")
            }

            // Mark session as inactive
            _isSessionActive.value = false
            lastActivityTime = 0L

            // Log security event
            auditLoggerProvider().logSecurityEvent(
                "Session ended: $reason",
                "INFO",
                AuditOutcome.SUCCESS
            )

            // Trigger UI logout callback
            onLogoutCallback?.invoke()

            Timber.tag(TAG).i("✅ Session ended successfully (KeyStore preserved)")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error during session cleanup")
            auditLoggerProvider().logSecurityEvent(
                "Session cleanup error: ${e.message}",
                "ERROR",
                AuditOutcome.FAILURE
            )
        }
    }

    /**
     * Update the last activity timestamp
     * Called on user interaction (touch, keyboard, etc.)
     */
    fun updateLastActivity() {
        if (_isSessionActive.value) {
            lastActivityTime = System.currentTimeMillis()
            Timber.tag(TAG).v("Activity updated: $lastActivityTime")
        }
    }

    /**
     * Check if the session has expired due to inactivity
     * @return true if session is expired or inactive, false otherwise
     */
    fun isSessionExpired(): Boolean {
        if (!_isSessionActive.value) return true

        val elapsed = System.currentTimeMillis() - lastActivityTime
        val expired = elapsed > sessionTimeoutMillis

        if (expired) {
            Timber.tag(TAG).w("⏱️ Session expired (inactive for ${elapsed / 1000}s)")
        }

        return expired
    }

    /**
     * Register callback to be invoked when session ends
     * Typically used to navigate to login screen from MainActivity
     */
    fun setOnLogoutCallback(callback: () -> Unit) {
        onLogoutCallback = callback
        Timber.tag(TAG).d("Logout callback registered")
    }

    /**
     * Check if user is currently authenticated
     * @return true if session is active and not expired
     */
    fun isAuthenticated(): Boolean {
        return _isSessionActive.value && !isSessionExpired()
    }

    // ========== UTILITY METHODS (FUTURE USE) ==========

    /**
     * Get time remaining until session expires
     * @return Time remaining in milliseconds, or 0 if expired
     */
    @Suppress("unused")
    fun getTimeUntilExpiry(): Long {
        if (!_isSessionActive.value) return 0L

        val elapsed = System.currentTimeMillis() - lastActivityTime
        val remaining = sessionTimeoutMillis - elapsed

        return if (remaining > 0) remaining else 0L
    }

    /**
     * Force refresh session timeout
     * Extends session by resetting activity timer
     */
    @Suppress("unused")
    fun refreshSession() {
        if (_isSessionActive.value) {
            updateLastActivity()
            Timber.tag(TAG).d("Session manually refreshed")
        }
    }
}
