package com.jcb.passbook.security.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
object KeystorePassphraseManager {

    private const val KEY_ALIAS = "passbook_db_key"
    private const val PREF_NAME = "db_key_prefs"
    private const val ENC_KEY = "enc_pass"

    fun getOrCreatePassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENC_KEY, null)
        return if (encrypted != null) {
            decryptPassphrase(encrypted)
        } else {
            val newPass = generateRandomPassphrase()
            val encryptedPass = encryptPassphrase(newPass)
            prefs.edit().putString(ENC_KEY, encryptedPass).apply()
            newPass
        }
    }

    fun rotatePassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val newPassphrase = generateRandomPassphrase()
        val encryptedPassphrase = encryptPassphrase(newPassphrase)
        prefs.edit().putString(ENC_KEY, encryptedPassphrase).apply()
        return newPassphrase
    }

    private fun generateRandomPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun getAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)   // Set to true if you want key use to require biometrics/lockscreen
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encryptPassphrase(pass: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getAesKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(pass.toByteArray(StandardCharsets.UTF_8))
        // Prepend IV to encrypted data for use in decryption
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptPassphrase(enc: String): String {
        val data = Base64.decode(enc, Base64.NO_WRAP)
        if (data.size <= 12) throw IllegalArgumentException("Invalid encrypted data")
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getAesKey(), GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(encrypted)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }
}