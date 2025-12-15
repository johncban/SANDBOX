package com.jcb.passbook.security.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "DatabaseKeyManager"

/**
 * DatabaseKeyManager - Manages database encryption keys
 *
 * ✅ CRITICAL FIX: Removed EncryptedSharedPreferences dependency
 * ✅ CRITICAL FIX: Now uses plain SharedPreferences + KeyStore wrapper key
 * ✅ CRITICAL FIX: Prevents EncryptedSharedPreferences from deleting KeyStore entries
 *
 * Architecture:
 * - Database Key (32 bytes random) → Encrypted with KeyStore wrapper key
 * - Encrypted Database Key + IV → Stored in plain SharedPreferences
 * - KeyStore Wrapper Key → Never deleted, persists across sessions
 */
class DatabaseKeyManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "passbook_database_key_wrapper"

    companion object {
        // ✅ CRITICAL FIX: Use PLAIN SharedPreferences, not EncryptedSharedPreferences
        private const val DATABASE_KEY_PREF_NAME = "passbook_database_key_prefs"
        private const val ENCRYPTED_KEY_PREF = "encrypted_database_key"
        private const val IV_PREF = "database_key_iv"
        private const val KEY_INITIALIZED_FLAG = "key_initialized"
        private const val TAG_LENGTH = 128
        private const val KEY_SIZE_BYTES = 32
    }

    /**
     * ✅ CRITICAL FIX: Use plain SharedPreferences instead of EncryptedSharedPreferences
     *
     * Previous Bug: EncryptedSharedPreferences was deleting KeyStore entries during migration
     * New Approach: Store encrypted key in plain SharedPreferences, use KeyStore for wrapping
     */
    private fun getKeyPrefs(): SharedPreferences {
        return context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * ✅ CRITICAL FIX: Get or create database passphrase WITHOUT DELETING EXISTING KEY
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        Timber.tag(TAG).d("=== getOrCreateDatabasePassphrase: START ===")
        return try {
            val prefs = getKeyPrefs()
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
            val prefs = getKeyPrefs()
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
            val prefs = getKeyPrefs()
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = prefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Timber.tag(TAG).d("No stored key found (first launch)")
                return null
            }

            Timber.tag(TAG).d("Found stored encrypted key, attempting to decrypt...")
            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            // ✅ CRITICAL: Check if wrapper key exists before attempting decrypt
            if (!keyStore.containsAlias(keyAlias)) {
                Timber.tag(TAG).e("❌ CRITICAL: Wrapper key missing from KeyStore!")
                return null
            }

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

            val prefs = getKeyPrefs()
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
        val wrapperKey = keyStore.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, wrapperKey, spec)
        return cipher.doFinal(encryptedKey)
    }

    /**
     * ✅ CRITICAL: Get or create the KeyStore wrapper key
     * This key NEVER gets deleted during session cleanup
     */
    private fun getOrCreateWrapperKey(): SecretKey {
        return if (keyStore.containsAlias(keyAlias)) {
            Timber.tag(TAG).d("✅ Using existing KeyStore wrapper key")
            keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            Timber.tag(TAG).w("⚠️ Wrapper key NOT found - generating NEW key (this will cause data loss!)")
            generateWrapperKey()
        }
    }

    /**
     * ✅ CRITICAL: Generate wrapper key with USER_AUTHENTICATION_REQUIRED = false
     * This prevents keys from being deleted when user logs out
     */
    private fun generateWrapperKey(): SecretKey {
        Timber.tag(TAG).i("Generating NEW wrapper key in Android Keystore...")

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
        val key = keyGenerator.generateKey()

        Timber.tag(TAG).i("✅ New wrapper key generated")
        return key
    }

    /**
     * Emergency key recovery attempt
     */
    private fun attemptEmergencyKeyRecovery(): ByteArray? {
        Timber.tag(TAG).w("🆘 Attempting emergency key recovery...")
        try {
            // Try to recover from old EncryptedSharedPreferences location
            val encryptedPrefs = try {
                val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()

                androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    "database_key_prefs_encrypted",
                    masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                null
            }

            val encryptedKeyBase64 = encryptedPrefs?.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = encryptedPrefs?.getString(IV_PREF, null)

            if (encryptedKeyBase64 != null && ivBase64 != null && keyStore.containsAlias(keyAlias)) {
                val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val recoveredKey = decryptKey(encryptedKey, iv)

                Timber.tag(TAG).i("✅ Emergency recovery successful from EncryptedSharedPreferences!")

                // Migrate to new plain SharedPreferences
                val prefs = getKeyPrefs()
                prefs.edit()
                    .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                    .putString(IV_PREF, ivBase64)
                    .putBoolean(KEY_INITIALIZED_FLAG, true)
                    .commit()

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
        return getKeyPrefs().getBoolean(KEY_INITIALIZED_FLAG, false)
    }

    /**
     * ⚠️ DANGEROUS: Clear database key - only use for factory reset
     */
    fun clearDatabaseKey() {
        try {
            Timber.tag(TAG).w("⚠️ Clearing database key - database will become inaccessible!")

            // Clear SharedPreferences
            val prefs = getKeyPrefs()
            prefs.edit().clear().commit()

            // ✅ CRITICAL: Also delete KeyStore wrapper key (factory reset only!)
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
                Timber.tag(TAG).d("KeyStore wrapper key deleted")
            }

            Timber.tag(TAG).i("✅ Database key cleared (factory reset)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear database key")
        }
    }
}
