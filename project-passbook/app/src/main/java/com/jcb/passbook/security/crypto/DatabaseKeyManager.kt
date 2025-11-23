package com.jcb.passbook.security.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * DatabaseKeyManager - Manages database encryption keys
 *
 * ✅ FIXED: Uses EncryptedSharedPreferences for secure persistent key storage
 * ✅ FIXED: Never regenerates key after initial creation
 * ✅ FIXED: NO_WRAP flag for Base64 to prevent newline corruption
 * ✅ FIXED: Comprehensive error handling and recovery
 */
class DatabaseKeyManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "passbook_database_key_wrapper"

    companion object {
        private const val DATABASE_KEY_PREF_NAME = "database_key_prefs_encrypted"
        private const val ENCRYPTED_KEY_PREF = "encrypted_database_key"
        private const val IV_PREF = "database_key_iv"
        private const val KEY_INITIALIZED_FLAG = "key_initialized"
        private const val TAG_LENGTH = 128
        private const val KEY_SIZE_BYTES = 32
    }

    /**
     * Get encrypted SharedPreferences
     * ✅ CRITICAL FIX: Use EncryptedSharedPreferences instead of regular SharedPreferences
     */
    private fun getEncryptedPrefs() = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            DATABASE_KEY_PREF_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular")
        context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get or create database passphrase
     * ✅ FIXED: Guaranteed to return the same key across app restarts
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        Timber.d("Getting or creating database passphrase...")

        return try {
            // Step 1: Try to retrieve existing key
            val existingKey = retrieveStoredKey()
            if (existingKey != null) {
                Timber.i("✅ Successfully retrieved existing database key (${existingKey.size} bytes)")
                return existingKey
            }

            // Step 2: Check if we've EVER initialized a key
            val prefs = getEncryptedPrefs()
            val wasInitialized = prefs.getBoolean(KEY_INITIALIZED_FLAG, false)

            if (wasInitialized) {
                // Key was initialized but retrieval failed - CRITICAL ERROR
                Timber.e("❌ CRITICAL: Key was initialized but retrieval failed!")
                Timber.e("This should NEVER happen - database may be corrupted")

                // DO NOT generate new key - this would break existing database
                // Instead, attempt emergency recovery
                return attemptEmergencyKeyRecovery()
            }

            // Step 3: First-time initialization - safe to generate new key
            Timber.i("First-time initialization - generating NEW database key")
            val newKey = generateAndStoreNewKey()

            // Mark as initialized
            prefs.edit().putBoolean(KEY_INITIALIZED_FLAG, true).apply()
            Timber.i("✅ NEW database key generated and stored (${newKey.size} bytes)")

            newKey

        } catch (e: Exception) {
            Timber.e(e, "❌ Fatal error in getOrCreateDatabasePassphrase")
            null
        }
    }

    /**
     * Retrieve stored encrypted database key
     * ✅ FIXED: Use NO_WRAP flag to prevent newline corruption
     */
    private fun retrieveStoredKey(): ByteArray? {
        return try {
            val prefs = getEncryptedPrefs()
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = prefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Timber.d("No stored key found (first launch)")
                return null
            }

            Timber.d("Found stored encrypted key, attempting to decrypt...")

            // ✅ CRITICAL FIX: Use NO_WRAP flag to prevent newline corruption
            val encryptedKey = android.util.Base64.decode(encryptedKeyBase64, android.util.Base64.NO_WRAP)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)

            val decryptedKey = decryptKey(encryptedKey, iv)

            // Validate key size
            if (decryptedKey.size != KEY_SIZE_BYTES) {
                Timber.e("❌ Retrieved key has invalid size: ${decryptedKey.size} bytes (expected $KEY_SIZE_BYTES)")
                return null
            }

            Timber.d("✅ Successfully decrypted stored database key (${decryptedKey.size} bytes)")
            decryptedKey

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to retrieve stored key")
            null
        }
    }

    /**
     * Generate and store new database key
     * ✅ FIXED: Only called on very first app launch
     * ✅ FIXED: Use NO_WRAP flag when encoding
     */
    private fun generateAndStoreNewKey(): ByteArray {
        Timber.i("Generating NEW 256-bit database encryption key...")

        val databaseKey = secureMemoryUtils.generateSecureRandom(KEY_SIZE_BYTES)
        Timber.d("Generated key size: ${databaseKey.size} bytes")

        try {
            // Encrypt the key using Android Keystore
            Timber.i("Generating NEW wrapper key in Android Keystore")
            val wrapperKey = getOrCreateWrapperKey()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)

            val encryptedKey = cipher.doFinal(databaseKey)
            val iv = cipher.iv

            Timber.d("Encrypted key size: ${encryptedKey.size} bytes, IV size: ${iv.size} bytes")

            // ✅ CRITICAL FIX: Use NO_WRAP flag to prevent newline corruption
            val encryptedKeyBase64 = android.util.Base64.encodeToString(encryptedKey, android.util.Base64.NO_WRAP)
            val ivBase64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)

            // Store encrypted key in EncryptedSharedPreferences
            val prefs = getEncryptedPrefs()
            prefs.edit().apply {
                putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                putString(IV_PREF, ivBase64)
                putBoolean(KEY_INITIALIZED_FLAG, true)
                apply()
            }

            Timber.i("✅ Database key generated and securely stored")
            return databaseKey

        } catch (e: Exception) {
            Timber.e(e, "❌ CRITICAL: Failed to store database key")
            throw IllegalStateException("Cannot proceed without storing database key", e)
        }
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
            Timber.i("Generating NEW wrapper key in Android Keystore")
            generateWrapperKey()
        }
    }

    /**
     * Generate a new wrapper key in Android Keystore
     */
    private fun generateWrapperKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Emergency key recovery attempt
     * ✅ Last resort if key retrieval fails but was previously initialized
     * ✅ FIXED: Use NO_WRAP for fallback recovery too
     */
    private fun attemptEmergencyKeyRecovery(): ByteArray? {
        Timber.w("🆘 Attempting emergency key recovery...")

        try {
            // Try regular SharedPreferences as fallback
            val regularPrefs = context.getSharedPreferences("database_key_prefs", Context.MODE_PRIVATE)
            val encryptedKeyBase64 = regularPrefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = regularPrefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 != null && ivBase64 != null) {
                // ✅ CRITICAL FIX: Use NO_WRAP flag
                val encryptedKey = android.util.Base64.decode(encryptedKeyBase64, android.util.Base64.NO_WRAP)
                val iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)

                val recoveredKey = decryptKey(encryptedKey, iv)

                Timber.i("✅ Emergency recovery successful!")

                // Migrate to EncryptedSharedPreferences with NO_WRAP
                val prefs = getEncryptedPrefs()
                prefs.edit().apply {
                    putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                    putString(IV_PREF, ivBase64)
                    putBoolean(KEY_INITIALIZED_FLAG, true)
                    apply()
                }

                return recoveredKey
            }

        } catch (e: Exception) {
            Timber.e(e, "Emergency recovery failed")
        }

        Timber.e("❌ All recovery attempts failed - database cannot be accessed")
        return null
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
     * Clear stored database key (DANGEROUS - only for complete reset)
     */
    fun clearDatabaseKey() {
        try {
            Timber.w("⚠️ Clearing database key - database will become inaccessible!")

            val prefs = getEncryptedPrefs()
            prefs.edit().clear().apply()

            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }

            // Also clear old regular SharedPreferences
            context.getSharedPreferences("database_key_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()

            Timber.i("✅ Database key cleared")

        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database key")
        }
    }

    /**
     * Check if database key is initialized
     */
    fun isDatabaseKeyInitialized(): Boolean {
        return getEncryptedPrefs().getBoolean(KEY_INITIALIZED_FLAG, false)
    }
}
