package com.jcb.passbook.security.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides access to session keys for database and encryption operations
 * Enforces session state validation before returning sensitive material
 */
@Singleton
class SessionKeyProvider @Inject constructor(
    private val sessionManager: SessionManager
) {
    /**
     * Exception thrown when attempting to access vault data without valid session
     */
    class SessionLockedException(message: String) : SecurityException(message)

    /**
     * Gets the current session passphrase for SQLCipher database access
     * @throws SessionLockedException if session is not unlocked
     */
    @Throws(SessionLockedException::class)
    fun requireSessionPassphrase(): CharArray {
        return sessionManager.getSessionPassphrase()
            ?: throw SessionLockedException("Vault access requires active session. Please unlock.")
    }

    /**
     * Gets the current session passphrase if available, null if locked
     */
    fun getSessionPassphraseOrNull(): CharArray? {
        return sessionManager.getSessionPassphrase()
    }

    /**
     * Gets current session ID for audit logging
     */
    fun getCurrentSessionId(): String? {
        return sessionManager.getCurrentSessionId()
    }

    /**
     * Checks if session is currently active
     */
    fun isSessionActive(): Boolean {
        return sessionManager.isUnlocked()
    }

    /**
     * Validates session state and throws if not unlocked
     * @throws SessionLockedException if session is locked
     */
    @Throws(SessionLockedException::class)
    fun requireActiveSession() {
        if (!sessionManager.isUnlocked()) {
            throw SessionLockedException("Operation requires active session. Current state: ${sessionManager.sessionState.value}")
        }
    }
}
