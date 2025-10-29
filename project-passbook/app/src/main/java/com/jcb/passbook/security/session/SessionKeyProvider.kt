package com.jcb.passbook.security.session

import androidx.annotation.GuardedBy
import com.jcb.passbook.security.crypto.CryptoManager
import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionKeyProvider @Inject constructor(
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val SESSION_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes
        private const val SESSION_ID_LENGTH = 16
        private const val SALT_LENGTH = 32
        private const val PBKDF2_ITERATIONS = 100000
        private const val KEY_LENGTH = 32
    }

    @GuardedBy("this")
    private var currentSessionId: String? = null

    @GuardedBy("this")
    private var sessionPassphrase: ByteArray? = null

    @GuardedBy("this")
    private var sessionStartTime: Long = 0L

    @GuardedBy("this")
    private var sessionSalt: ByteArray? = null

    private val secureRandom = SecureRandom()

    class SessionLockedException(message: String) : Exception(message)

    /**
     * Creates a new session with the provided passphrase
     */
    @Synchronized
    fun createSession(userPassphrase: ByteArray): String {
        try {
            // Clear any existing session
            clearSession()

            // Generate new session data
            val sessionId = generateSessionId()
            val salt = generateSalt()
            val derivedPassphrase = deriveKey(userPassphrase, salt)

            // Store session data
            currentSessionId = sessionId
            sessionPassphrase = derivedPassphrase
            sessionSalt = salt
            sessionStartTime = System.currentTimeMillis()

            Timber.d("Session created successfully: ${sessionId.take(8)}...")
            return sessionId
        } catch (e: Exception) {
            Timber.e(e, "Failed to create session")
            throw SessionLockedException("Failed to create session: ${e.message}")
        }
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
     * Gets session salt, throws if session is not active
     */
    @Synchronized
    @Throws(SessionLockedException::class)
    fun requireSessionSalt(): ByteArray {
        if (isSessionExpired()) {
            clearSession()
            throw SessionLockedException("Session expired")
        }
        return sessionSalt?.copyOf()
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

        sessionPassphrase?.let { passphrase ->
            passphrase.fill(0.toByte())
        }
        sessionPassphrase = null

        sessionSalt?.let { salt ->
            salt.fill(0.toByte())
        }
        sessionSalt = null

        sessionStartTime = 0L
        Timber.d("Session cleared")
    }

    /**
     * Extend session timeout by updating the start time
     */
    @Synchronized
    fun extendSession() {
        if (currentSessionId != null && !isSessionExpired()) {
            sessionStartTime = System.currentTimeMillis()
            Timber.d("Session extended: ${currentSessionId?.take(8)}...")
        } else {
            Timber.w("Attempted to extend invalid or expired session")
        }
    }

    /**
     * Checks if session is locked (exists but expired)
     */
    @Synchronized
    fun isSessionLocked(): Boolean {
        return currentSessionId != null && isSessionExpired()
    }

    /**
     * Checks if there's an active (non-expired) session
     */
    @Synchronized
    fun hasActiveSession(): Boolean {
        return currentSessionId != null && !isSessionExpired()
    }

    /**
     * Gets remaining session time in milliseconds
     */
    @Synchronized
    fun getRemainingSessionTime(): Long {
        if (currentSessionId == null) return 0L
        val elapsed = System.currentTimeMillis() - sessionStartTime
        return maxOf(0L, SESSION_TIMEOUT_MS - elapsed)
    }

    /**
     * Gets session duration in milliseconds
     */
    @Synchronized
    fun getSessionDuration(): Long {
        return if (currentSessionId != null) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            0L
        }
    }

    // Private helper methods

    private fun generateSessionId(): String {
        val bytes = ByteArray(SESSION_ID_LENGTH)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }

    private fun deriveKey(passphrase: ByteArray, salt: ByteArray): ByteArray {
        return try {
            val spec = PBEKeySpec(
                passphrase.toString(Charsets.UTF_8).toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_LENGTH * 8
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val key = factory.generateSecret(spec)
            key.encoded
        } catch (e: Exception) {
            Timber.e(e, "Failed to derive key")
            throw SessionLockedException("Key derivation failed: ${e.message}")
        }
    }

    private fun isSessionExpired(): Boolean {
        return currentSessionId != null &&
                (System.currentTimeMillis() - sessionStartTime) > SESSION_TIMEOUT_MS
    }
}