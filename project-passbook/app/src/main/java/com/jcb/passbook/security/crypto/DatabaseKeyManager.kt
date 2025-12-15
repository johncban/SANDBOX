package com.jcb.passbook.security.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "DatabaseKeyManager"

/**
 * DatabaseKeyManager - Manages database encryption keys
 * ✅ FIXED: Persistent key storage - keys NO LONGER deleted on app restart
 * ✅ FIXED: Proper initialization flag to detect first-time vs existing keys
 * ✅ FIXED: Emergency recovery for corrupted key scenarios
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
     * ✅ FIXED: Use new MasterKey API instead of deprecated MasterKeys
     */
    private fun getEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                DATABASE_KEY_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create EncryptedSharedPreferences, falling back to regular")
            context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * ✅ CRITICAL FIX: Get or create database passphrase WITHOUT DELETING EXISTING KEY
     *
     * Previous Bug: Keys were being deleted on every app startup
     * Fix: Check initialization flag FIRST, only generate new key if never initialized
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        Timber.tag(TAG).d("=== getOrCreateDatabasePassphrase: START ===")
        return try {
            val prefs = getEncryptedPrefs()
            val wasInitialized = prefs.getBoolean(KEY_INITIALIZED_FLAG, false)

            if (wasInitialized) {
                // ✅ Key exists - retrieve it WITHOUT regenerating
                Timber.tag(TAG).d("Database key was previously initialized - retrieving existing key")
                val existingKey = retrieveStoredKey()

                if (existingKey != null) {
                    Timber.tag(TAG).i("✅ Successfully retrieved existing database key (${existingKey.size} bytes)")
                    return existingKey
                } else {
                    // Key flag set but key missing - corruption scenario
                    Timber.tag(TAG).e("❌ CRITICAL: Key was initialized but retrieval failed!")
                    return attemptEmergencyKeyRecovery()
                }
            }

            // ✅ First-time initialization ONLY
            Timber.tag(TAG).i("First-time initialization - generating NEW database key")
            val newKey = generateAndStoreNewKey()
            Timber.tag(TAG).i("✅ NEW database key generated and stored (${newKey.size} bytes)")
            newKey

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Fatal error in getOrCreateDatabasePassphrase")
            null
        } finally {
            Timber.tag(TAG).d("=== getOrCreateDatabasePassphrase: END ===")
        }
    }

    /**
     * ✅ FIXED: Get current database passphrase as ByteArray
     */
    fun getCurrentDatabasePassphrase(): ByteArray? {
        return try {
            val prefs = getEncryptedPrefs()
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = prefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Timber.tag(TAG).d("No existing database passphrase found")
                return null
            }

            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val decryptedKey = decryptKey(encryptedKey, iv)

            if (decryptedKey.size == KEY_SIZE_BYTES) {
                Timber.tag(TAG).d("Successfully retrieved current passphrase (${decryptedKey.size} bytes)")
                return decryptedKey
            } else {
                Timber.tag(TAG).w("Decrypted key is invalid or wrong size: ${decryptedKey.size}")
                return null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error retrieving current passphrase")
            null
        }
    }

    /**
     * Retrieve stored encrypted database key
     */
    private fun retrieveStoredKey(): ByteArray? {
        return try {
            val prefs = getEncryptedPrefs()
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = prefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Timber.tag(TAG).d("No stored key found (first launch)")
                return null
            }

            Timber.tag(TAG).d("Found stored encrypted key, attempting to decrypt...")
            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val decryptedKey = decryptKey(encryptedKey, iv)

            if (decryptedKey.size != KEY_SIZE_BYTES) {
                Timber.tag(TAG).e("❌ Retrieved key has invalid size: ${decryptedKey.size} bytes (expected $KEY_SIZE_BYTES)")
                return null
            }

            Timber.tag(TAG).d("✅ Successfully decrypted stored database key (${decryptedKey.size} bytes)")
            decryptedKey
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to retrieve stored key")
            null
        }
    }

    /**
     * ✅ CRITICAL FIX: Generate and store new database key with commit() for synchronous write
     */
    private fun generateAndStoreNewKey(): ByteArray {
        Timber.tag(TAG).i("Generating NEW 256-bit database encryption key...")
        val databaseKey = secureMemoryUtils.generateSecureRandom(KEY_SIZE_BYTES)
        Timber.tag(TAG).d("Generated key size: ${databaseKey.size} bytes")

        try {
            val wrapperKey = getOrCreateWrapperKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)
            val encryptedKey = cipher.doFinal(databaseKey)
            val iv = cipher.iv

            Timber.tag(TAG).d("Encrypted key size: ${encryptedKey.size} bytes, IV size: ${iv.size} bytes")

            val encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            val prefs = getEncryptedPrefs()
            // ✅ CRITICAL: Use commit() for synchronous write on first-time initialization
            val success = prefs.edit()
                .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                .putString(IV_PREF, ivBase64)
                .putBoolean(KEY_INITIALIZED_FLAG, true) // ✅ Set initialization flag
                .commit()

            if (!success) {
                Timber.tag(TAG).e("❌ CRITICAL: Failed to commit initial database key!")
                throw IllegalStateException("Failed to commit initial database key to storage")
            }

            Timber.tag(TAG).i("✅ Database key generated and securely stored")
            return databaseKey

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ CRITICAL: Failed to store database key")
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
            Timber.tag(TAG).i("Generating NEW wrapper key in Android Keystore")
            generateWrapperKey()
        }
    }

    /**
     * ✅ CRITICAL: Generate wrapper key with USER_AUTHENTICATION_REQUIRED = false
     * This prevents keys from being deleted when user logs out
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
            .setUserAuthenticationRequired(false) // ✅ CRITICAL: Don't tie to user auth
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Emergency key recovery attempt
     */
    private fun attemptEmergencyKeyRecovery(): ByteArray? {
        Timber.tag(TAG).w("🆘 Attempting emergency key recovery...")
        try {
            val regularPrefs = context.getSharedPreferences("database_key_prefs", Context.MODE_PRIVATE)
            val encryptedKeyBase64 = regularPrefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = regularPrefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 != null && ivBase64 != null) {
                val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val recoveredKey = decryptKey(encryptedKey, iv)

                Timber.tag(TAG).i("✅ Emergency recovery successful!")

                // Restore to encrypted prefs
                val prefs = getEncryptedPrefs()
                prefs.edit()
                    .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                    .putString(IV_PREF, ivBase64)
                    .putBoolean(KEY_INITIALIZED_FLAG, true)
                    .commit() // Use commit for critical recovery

                return recoveredKey
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Emergency recovery failed")
        }

        Timber.tag(TAG).e("❌ All recovery attempts failed - database cannot be accessed")
        return null
    }

    /**
     * Check if database key is initialized
     */
    fun isDatabaseKeyInitialized(): Boolean {
        return getEncryptedPrefs().getBoolean(KEY_INITIALIZED_FLAG, false)
    }

    /**
     * ⚠️ DANGEROUS: Clear database key - only use for factory reset
     */
    fun clearDatabaseKey() {
        try {
            Timber.tag(TAG).w("⚠️ Clearing database key - database will become inaccessible!")
            val prefs = getEncryptedPrefs()
            prefs.edit().clear().apply()


            context.getSharedPreferences("database_key_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()

            Timber.tag(TAG).i("✅ Database key cleared")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear database key")
        }
    }
}
