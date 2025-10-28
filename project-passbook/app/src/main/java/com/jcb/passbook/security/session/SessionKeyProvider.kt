package com.jcb.passbook.security.session

import androidx.annotation.GuardedBy
import com.jcb.passbook.security.crypto.CryptoManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionKeyProvider @Inject constructor(
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val SESSION_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes
    }

    @GuardedBy("this")
    private var currentSessionId: String? = null

    @GuardedBy("this")
    private var sessionPassphrase: ByteArray? = null

    @GuardedBy("this")
    private var sessionStartTime: Long = 0L

    class SessionLockedException(message: String) : Exception(message)

    /**
     * Creates a new session with the provided passphrase
     */
    @Synchronized
    fun createSession(userPassphrase: ByteArray): String {
        // Generate session ID
        val sessionId = cryptoManager.generateRandomKey().joinToString("") {
            "%02x".format(it)
        }.take(16)

        // Derive session passphrase from user passphrase
        val salt = cryptoManager.generateDerivationSalt()
        val derivedPassphrase = cryptoManager.deriveKeyArgon2id(userPassphrase, salt)

        // Store session data
        currentSessionId = sessionId
        sessionPassphrase = derivedPassphrase
        sessionStartTime = System.currentTimeMillis()

        Timber.d("Session created: $sessionId")
        return sessionId
    }

    /**
     * Gets current session ID if session is active
     */
    @Synchronized
    fun getCurrentSessionId(): String? {
        if (isSessionExpired()) {
            clearSession()
            return null
        }
        return currentSessionId
    }

    /**
     * Gets session passphrase, throws if session is not active
     */
    @Synchronized
    @Throws(SessionLockedException::class)
    fun requireSessionPassphrase(): ByteArray {
        if (isSessionExpired()) {
            clearSession()
            throw SessionLockedException("Session expired")
        }

        return sessionPassphrase?.copyOf()
            ?: throw SessionLockedException("No active session")
    }

    /**
     * Validates active session exists
     */
    @Synchronized
    @Throws(SessionLockedException::class)
    fun requireActiveSession() {
        if (isSessionExpired()) {
            clearSession()
            throw SessionLockedException("Session expired")
        }

        if (currentSessionId == null) {
            throw SessionLockedException("No active session")
        }
    }

    /**
     * Clears current session
     */
    @Synchronized
    fun clearSession() {
        currentSessionId = null
        sessionPassphrase?.fill(0.toByte())
        sessionPassphrase = null
        sessionStartTime = 0L
        Timber.d("Session cleared")
    }

    /**
     * Extends current session timeout
     */
    @Synchronized
    fun extendSession() {
        if (currentSessionId != null && !isSessionExpired()) {
            sessionStartTime = System.currentTimeMillis()
            Timber.d("Session extended")
        }
    }

    private fun isSessionExpired(): Boolean {
        return currentSessionId != null &&
                (System.currentTimeMillis() - sessionStartTime) > SESSION_TIMEOUT_MS
    }
}
