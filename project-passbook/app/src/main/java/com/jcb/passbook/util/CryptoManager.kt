package com.jcb.passbook.util

import android.annotation.SuppressLint
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import timber.log.Timber

private const val TAG = "PassBook CryptoManager"

@Singleton
open class CryptoManager @Inject constructor() {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val ALIAS = "password_data_key_alias"

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getKey(): SecretKey {
        val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: createKey()
    }

    class EncryptionException(message: String, cause: Throwable) : Exception(message, cause)


    @RequiresApi(Build.VERSION_CODES.M)
    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @SuppressLint("TimberArgCount")
    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(Exception::class)
    open fun encrypt(plainText: String): ByteArray {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            require(iv.size == 12) { "IV must be 12 bytes for GCM" }
            return iv + encrypted
        } catch (e: Exception) {
            Timber.e(TAG, "Encryption failed: ${e.localizedMessage}", e)
            throw EncryptionException("Encryption failed", e)
        }
    }

    @SuppressLint("TimberArgCount")
    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(Exception::class)
    open fun decrypt(data: ByteArray): String {
        if (data.size <= 12) throw IllegalArgumentException("Data too short to contain IV and ciphertext")
        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(TAG, "Decryption failed: ${e.localizedMessage}", e)
            throw e
        }
    }

}
