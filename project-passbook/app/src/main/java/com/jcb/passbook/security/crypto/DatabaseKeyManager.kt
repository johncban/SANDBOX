package com.jcb.passbook.security.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * DatabaseKeyManager - Manages database encryption keys
 *
 * FIXED: Database key is created independently of session state
 * Session is only required for user data access, not DB initialization
 */
class DatabaseKeyManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "passbook_database_key_wrapper"

    companion object {
        private const val DATABASE_KEY_PREF_NAME = "database_key_prefs"
        private const val ENCRYPTED_KEY_PREF = "encrypted_database_key"
        private const val IV_PREF = "database_key_iv"
        private const val TAG_LENGTH = 128
    }

    /**
     * Get or create database passphrase
     * ✅ FIXED: No longer requires active session for initial database creation
     * Session check is deferred until user actually tries to access data
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        Timber.d("Getting or creating database passphrase")

        return try {
            val existingKey = retrieveStoredKey()
            if (existingKey != null) {
                Timber.d("Retrieved existing database key")
                existingKey
            } else {
                Timber.d("Generating new database key")
                generateAndStoreNewKey()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get/create database passphrase")
            null
        }
    }

    /**
     * Retrieve stored encrypted database key
     */
    private fun retrieveStoredKey(): ByteArray? {
        return try {
            val prefs = context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null) ?: return null
            val ivBase64 = prefs.getString(IV_PREF, null) ?: return null

            val encryptedKey = android.util.Base64.decode(encryptedKeyBase64, android.util.Base64.DEFAULT)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.DEFAULT)

            decryptKey(encryptedKey, iv)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve stored key")
            null
        }
    }

    /**
     * Generate and store new database key
     * ✅ This happens on first app launch, before any session exists
     */
    private fun generateAndStoreNewKey(): ByteArray {
        val databaseKey = secureMemoryUtils.generateSecureRandom(32)

        // Encrypt the key using Android Keystore
        val wrapperKey = getOrCreateWrapperKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)

        val encryptedKey = cipher.doFinal(databaseKey)
        val iv = cipher.iv

        // Store encrypted key
        val prefs = context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(ENCRYPTED_KEY_PREF, android.util.Base64.encodeToString(encryptedKey, android.util.Base64.DEFAULT))
            putString(IV_PREF, android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT))
            apply()
        }

        Timber.i("Generated and stored new database key for first-time initialization")
        return databaseKey
    }

    /**
     * Decrypt stored database key
     */
    private fun decryptKey(encryptedKey: ByteArray, iv: ByteArray): ByteArray {
        val wrapperKey = getOrCreateWrapperKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, wrapperKey, spec)

        return cipher.doFinal(encryptedKey)
    }

    /**
     * Get or create the KeyStore wrapper key
     */
    private fun getOrCreateWrapperKey(): SecretKey {
        return if (keyStore.containsAlias(keyAlias)) {
            keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            generateWrapperKey()
        }
    }

    /**
     * Generate a new wrapper key in Android Keystore
     */
    private fun generateWrapperKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // ✅ No auth needed for DB wrapper key
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Check if user has active session (for data access control)
     */
    fun requireActiveSession(): Boolean {
        if (!sessionManager.isSessionActive()) {
            Timber.w("No active session - user must log in")
            return false
        }
        return true
    }

    /**
     * Clear stored database key (for app uninstall/reset)
     */
    fun clearDatabaseKey() {
        try {
            val prefs = context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }

            Timber.i("Cleared database key")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database key")
        }
    }
}
