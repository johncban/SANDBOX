package com.jcb.passbook.security.biometric

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val AUTH_TIMEOUT_SECONDS = 60 // require biometrics again after 60s

    fun aliasForUser(userId: Int) = "PB_BIOMETRIC_AES_$userId"

    @RequiresApi(Build.VERSION_CODES.R)
    fun createKeyIfNeeded(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) return

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(AUTH_TIMEOUT_SECONDS, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGen.init(spec)
        keyGen.generateKey()
    }

    fun getEncryptCipher(alias: String): Cipher {
        val cipher = Cipher.getInstance(AES_MODE)
        val key = getKey(alias)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun getDecryptCipher(alias: String, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(AES_MODE)
        val key = getKey(alias)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher
    }

    private fun getKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(alias, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun deleteKey(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }
}
