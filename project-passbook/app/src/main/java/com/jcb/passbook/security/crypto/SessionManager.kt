package com.jcb.passbook.security.crypto

import android.content.Context
import timber.log.Timber
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SessionManager(
    private val context: Context,
    private val keystoreManager: KeystorePassphraseManager,
    private val cryptoManager: CryptoManager
) {
    data class Session(
        val id: String,
        val userId: Long,
        val createdAt: Long
    ) {
        fun isExpired(timeoutMs: Long): Boolean =
            System.currentTimeMillis() - createdAt > timeoutMs
    }

    private var currentSession: Session? = null

    fun startSession(userId: Long) {
        currentSession = Session(
            id = java.util.UUID.randomUUID().toString(),
            userId = userId,
            createdAt = System.currentTimeMillis()
        )
        Timber.d("Session started for user $userId")
    }

    fun endSession() {
        currentSession = null
        Timber.d("Session ended")
    }

    fun isSessionActive(): Boolean =
        currentSession?.isExpired(SESSION_TIMEOUT_MS)?.not() == true

    fun getSessionId(): String =
        currentSession?.id ?: throw IllegalStateException("No active session")

    fun getUserId(): Long =
        currentSession?.userId ?: throw IllegalStateException("No active user")

    fun getEphemeralSessionKey(): SecretKeySpec? = try {
        val passphrase = keystoreManager.getPassphrase() ?: return null
        val amk = keystoreManager.deriveAMK(passphrase)

        val mac = Mac.getInstance("HmacSHA256")
        val macKey = SecretKeySpec(amk, "HmacSHA256")
        mac.init(macKey)

        val input = (getSessionId() + currentSession?.createdAt).toString()
        val eskBytes = mac.doFinal(input.toByteArray())

        SecretKeySpec(eskBytes.copyOf(32), "AES")
    } catch (e: Exception) {
        Timber.e(e, "Failed to derive ephemeral session key")
        null
    }

    fun isEphemeralKeyValid(): Boolean = isSessionActive()

    companion object {
        private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
