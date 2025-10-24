package com.jcb.passbook.security.biometric

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt

object BiometricLoginManager {

    @RequiresApi(Build.VERSION_CODES.R)
    fun provisionAfterRegistration(
        context: Context,
        userId: Int,
        onComplete: (Boolean) -> Unit
    ) {
        // Create key and encrypt a fresh random token
        val alias = BiometricKeyManager.aliasForUser(userId)
        BiometricKeyManager.createKeyIfNeeded(alias)
        val cipher = BiometricKeyManager.getEncryptCipher(alias)
        val token = generateToken()
        val ct = cipher.doFinal(token)
        BiometricTokenStore.save(context, userId, cipher.iv, ct)
        onComplete(true)
    }

    fun promptAndLogin(
        activity: FragmentActivity,
        userId: Int,
        onDecryptedToken: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        val stored = BiometricTokenStore.load(activity, userId)
            ?: return onError("No biometric token")

        val alias = BiometricKeyManager.aliasForUser(userId)
        val cipher = try {
            BiometricKeyManager.getDecryptCipher(alias, stored.iv)
        } catch (t: Throwable) {
            return onError("Key unavailable")
        }

        val prompt = BiometricHelper.buildPrompt(
            activity = activity,
            title = "Biometric login",
            negative = "Use password",
            onSuccess = { result ->
                try {
                    val pt = result.cryptoObject?.cipher?.doFinal(stored.cipherText)
                    if (pt != null) onDecryptedToken(pt) else onError("Crypto error")
                } catch (t: Throwable) {
                    onError("Decrypt failed")
                }
            },
            onError = { _, msg -> onError(msg.toString()) }
        )
        val info = BiometricHelper.buildPromptInfo(title = "Biometric login", negative = "Use password")
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    fun clearForUser(context: Context, userId: Int) {
        BiometricTokenStore.clear(context, userId)
        BiometricKeyManager.deleteKey(BiometricKeyManager.aliasForUser(userId))
    }

    private fun generateToken(): ByteArray {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }
}