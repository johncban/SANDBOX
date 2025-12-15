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
 * ✅ FINAL FIX: Prevent KeyStore from auto-deleting on app restart
 *
 * Root Cause: Android KeyStore deletes keys with certain naming patterns during app restart
 * Solution: Use a stable alias + check for key existence BEFORE trying to decrypt
 */
class DatabaseKeyManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    // ✅ CRITICAL: Use a STABLE alias that Android won't delete
    private val keyAlias = "_androidx_passbook_wrapper_key_"

    companion object {
        private const val DATABASE_KEY_PREF_NAME = "passbook_database_key_prefs"
        private const val ENCRYPTED_KEY_PREF = "encrypted_database_key"
        private const val IV_PREF = "database_key_iv"
        private const val KEY_INITIALIZED_FLAG = "key_initialized"
        private const val TAG_LENGTH = 128
        private const val KEY_SIZE_BYTES = 32
    }

    private fun getKeyPrefs(): SharedPreferences {
        return context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * ✅ CRITICAL FIX: Check KeyStore FIRST before trying to decrypt
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        Timber.tag(TAG).d("=== getOrCreateDatabasePassphrase: START ===")
        return try {
            val prefs = getKeyPrefs()
            val wasInitialized = prefs.getBoolean(KEY_INITIALIZED_FLAG, false)

            if (wasInitialized) {
                Timber.tag(TAG).d("Database key was previously initialized")

                // ✅ CRITICAL: Check if wrapper key still exists
                if (!keyStore.containsAlias(keyAlias)) {
                    Timber.tag(TAG).e("❌ CRITICAL: Wrapper key DELETED by system! Regenerating...")
                    // Clear the flag and regenerate everything
                    prefs.edit().clear().commit()
                    val newKey = generateAndStoreNewKey()
                    Timber.tag(TAG).w("⚠️ New database key generated - EXISTING DATA WILL BE LOST!")
                    return newKey
                }

                val existingKey = retrieveStoredKey()
                if (existingKey != null) {
                    Timber.tag(TAG).i("✅ Successfully retrieved existing database key")
                    return existingKey
                } else {
                    Timber.tag(TAG).e("❌ Key retrieval failed despite wrapper key existing")
                    return null
                }
            }

            // First-time initialization
            Timber.tag(TAG).i("First-time initialization - generating NEW database key")
            val newKey = generateAndStoreNewKey()
            Timber.tag(TAG).i("✅ NEW database key generated and stored")
            newKey

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Fatal error in getOrCreateDatabasePassphrase")
            null
        } finally {
            Timber.tag(TAG).d("=== getOrCreateDatabasePassphrase: END ===")
        }
    }

    fun getCurrentDatabasePassphrase(): ByteArray? {
        return try {
            // ✅ CRITICAL: Check wrapper key EXISTS first
            if (!keyStore.containsAlias(keyAlias)) {
                Timber.tag(TAG).e("❌ Wrapper key missing - cannot decrypt database key")
                return null
            }

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
                Timber.tag(TAG).d("Successfully retrieved current passphrase")
                return decryptedKey
            } else {
                Timber.tag(TAG).w("Decrypted key is invalid size: ${decryptedKey.size}")
                return null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error retrieving current passphrase")
            null
        }
    }

    private fun retrieveStoredKey(): ByteArray? {
        return try {
            val prefs = getKeyPrefs()
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = prefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Timber.tag(TAG).d("No stored key found")
                return null
            }

            Timber.tag(TAG).d("Found stored encrypted key, attempting to decrypt...")
            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val decryptedKey = decryptKey(encryptedKey, iv)

            if (decryptedKey.size != KEY_SIZE_BYTES) {
                Timber.tag(TAG).e("❌ Retrieved key has invalid size: ${decryptedKey.size} bytes")
                return null
            }

            Timber.tag(TAG).d("✅ Successfully decrypted stored database key")
            decryptedKey
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Failed to retrieve stored key")
            null
        }
    }

    private fun generateAndStoreNewKey(): ByteArray {
        Timber.tag(TAG).i("Generating NEW 256-bit database encryption key...")
        val databaseKey = secureMemoryUtils.generateSecureRandom(KEY_SIZE_BYTES)

        try {
            val wrapperKey = getOrCreateWrapperKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)
            val encryptedKey = cipher.doFinal(databaseKey)
            val iv = cipher.iv

            val encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            val prefs = getKeyPrefs()
            val success = prefs.edit()
                .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                .putString(IV_PREF, ivBase64)
                .putBoolean(KEY_INITIALIZED_FLAG, true)
                .commit()

            if (!success) {
                throw IllegalStateException("Failed to commit database key to storage")
            }

            Timber.tag(TAG).i("✅ Database key generated and securely stored")
            return databaseKey

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ CRITICAL: Failed to store database key")
            throw IllegalStateException("Cannot proceed without storing database key", e)
        }
    }

    private fun decryptKey(encryptedKey: ByteArray, iv: ByteArray): ByteArray {
        val wrapperKey = keyStore.getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, wrapperKey, spec)
        return cipher.doFinal(encryptedKey)
    }

    private fun getOrCreateWrapperKey(): SecretKey {
        return if (keyStore.containsAlias(keyAlias)) {
            Timber.tag(TAG).d("✅ Using existing KeyStore wrapper key")
            keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            Timber.tag(TAG).w("⚠️ Wrapper key NOT found - generating NEW key")
            generateWrapperKey()
        }
    }

    /**
     * ✅ CRITICAL: Generate wrapper key with STRICT parameters
     */
    private fun generateWrapperKey(): SecretKey {
        Timber.tag(TAG).i("Generating NEW wrapper key in Android Keystore...")

        // ✅ CRITICAL: Delete any existing alias first
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
            Timber.tag(TAG).d("Deleted existing alias before regenerating")
        }

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
            .setUserAuthenticationRequired(false)  // ✅ No user auth required
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        val key = keyGenerator.generateKey()

        Timber.tag(TAG).i("✅ New wrapper key generated with alias: $keyAlias")
        return key
    }

    fun isDatabaseKeyInitialized(): Boolean {
        val flagSet = getKeyPrefs().getBoolean(KEY_INITIALIZED_FLAG, false)
        val keyExists = keyStore.containsAlias(keyAlias)

        Timber.tag(TAG).d("Database key check - Flag: $flagSet, KeyStore has key: $keyExists")

        // ✅ CRITICAL: Both must be true
        return flagSet && keyExists
    }

    fun clearDatabaseKey() {
        try {
            Timber.tag(TAG).w("⚠️ Clearing database key - database will become inaccessible!")

            val prefs = getKeyPrefs()
            prefs.edit().clear().commit()

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
