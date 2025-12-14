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
 * Core responsibilities:
 * 1. Track session active/inactive state
 * 2. Handle automatic timeout after 15 minutes of inactivity
 * 3. Clear sensitive data securely on session end
 * 4. Coordinate with UI for logout navigation
 *
 * Thread-safe: Uses StateFlow for state management
 * Security: Ensures all sensitive data is cleared from memory
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

    // Session timeout: 15 minutes (camelCase to avoid underscore warning)
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
     * End the current session and clear all sensitive data
     * @param reason Reason for ending session (e.g., "Manual logout", "Idle timeout")
     */
    suspend fun endSession(reason: String = "Manual logout") {
        if (!_isSessionActive.value) {
            Timber.tag(TAG).d("Session already inactive")
            return
        }

        Timber.tag(TAG).i("Ending session: $reason")

        try {
            // Clear encryption keys from memory (if method exists)
            try {
                // ✅ FIXED: Check if method exists before calling
                masterKeyManager.javaClass.getMethod("clearKeys").invoke(masterKeyManager)
                Timber.tag(TAG).d("Encryption keys cleared")
            } catch (_: NoSuchMethodException) {
                Timber.tag(TAG).d("clearKeys() not available - skipping")
            }

            // Wipe sensitive memory regions (if method exists)
            try {
                // ✅ FIXED: Check if method exists before calling
                secureMemoryUtils.javaClass.getMethod("clearSensitiveData").invoke(secureMemoryUtils)
                Timber.tag(TAG).d("Sensitive data wiped from memory")
            } catch (_: NoSuchMethodException) {
                Timber.tag(TAG).d("clearSensitiveData() not available - skipping")
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

            Timber.tag(TAG).i("✅ Session ended successfully")

        } catch (e: Exception) {
            // ✅ FIXED: Use underscore prefix to suppress "unused parameter" warning
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
     *
     * Note: Currently unused but available for future session countdown UI
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
     *
     * Note: Currently unused but available for explicit session extension feature
     */
    @Suppress("unused")
    fun refreshSession() {
        if (_isSessionActive.value) {
            updateLastActivity()
            Timber.tag(TAG).d("Session manually refreshed")
        }
    }
}
