
package com.jcb.passbook.util.encryption

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.Context
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionUtilImpl @Inject constructor(
    private val context: Context
) : EncryptionUtil {

    private val algorithm = "AES/GCB/NoPadding"
    private val keyAlias = "PassbookMasterKey"

    private val masterKey by lazy {
        MasterKey.Builder(context, keyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    override fun encrypt(plaintext: String): ByteArray {
        try {
            val cipher = Cipher.getInstance(algorithm)
            val keySpec = SecretKeySpec(getMasterKeyBytes(), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Combine IV and encrypted data
            return iv + encryptedData
        } catch (e: Exception) {
            throw RuntimeException("Encryption failed", e)
        }
    }

    override fun decrypt(ciphertext: ByteArray): String {
        try {
            val cipher = Cipher.getInstance(algorithm)
            val keySpec = SecretKeySpec(getMasterKeyBytes(), "AES")

            // Extract IV (first 12 bytes for GCM)
            val iv = ciphertext.sliceArray(0..11)
            val encryptedData = ciphertext.sliceArray(12 until ciphertext.size)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, iv))
            val decryptedData = cipher.doFinal(encryptedData)

            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            throw RuntimeException("Decryption failed", e)
        }
    }

    private fun getMasterKeyBytes(): ByteArray {
        // This is a simplified implementation
        // In production, you should use Android Keystore or similar secure storage
        val preferences = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var keyBytes = preferences.getString("master_key", null)
        if (keyBytes == null) {
            // Generate new key
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val secretKey = keyGen.generateKey()
            keyBytes = android.util.Base64.encodeToString(secretKey.encoded, android.util.Base64.DEFAULT)
            preferences.edit().putString("master_key", keyBytes).apply()
        }

        return android.util.Base64.decode(keyBytes, android.util.Base64.DEFAULT)
    }
}