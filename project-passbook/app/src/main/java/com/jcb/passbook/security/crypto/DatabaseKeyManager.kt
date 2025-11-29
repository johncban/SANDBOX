// @/app/src/main/java/com/jcb/passbook/security/crypto/DatabaseKeyManager.kt

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

/**
 * DatabaseKeyManager - Manages database encryption keys
 * ✅ FIXED: Complete key rotation support with rollback
 * ✅ FIXED: Updated to use non-deprecated MasterKey API
 * ✅ FIXED: Proper commit() return handling
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
            // ✅ Use new MasterKey API (not deprecated)
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
            Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular")
            context.getSharedPreferences(DATABASE_KEY_PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Get or create database passphrase
     */
    suspend fun getOrCreateDatabasePassphrase(): ByteArray? {
        Timber.d("Getting or creating database passphrase...")

        return try {
            val existingKey = retrieveStoredKey()
            if (existingKey != null) {
                Timber.i("✅ Successfully retrieved existing database key (${existingKey.size} bytes)")
                return existingKey
            }

            val prefs = getEncryptedPrefs()
            val wasInitialized = prefs.getBoolean(KEY_INITIALIZED_FLAG, false)

            if (wasInitialized) {
                Timber.e("❌ CRITICAL: Key was initialized but retrieval failed!")
                return attemptEmergencyKeyRecovery()
            }

            Timber.i("First-time initialization - generating NEW database key")
            val newKey = generateAndStoreNewKey()
            // No need to set the flag here, as generateAndStoreNewKey now handles it.
            Timber.i("✅ NEW database key generated and stored (${newKey.size} bytes)")
            newKey

        } catch (e: Exception) {
            Timber.e(e, "❌ Fatal error in getOrCreateDatabasePassphrase")
            null
        }
    }

    /**
     * ✅ FIXED: Get current database passphrase as ByteArray.
     * This function was completely broken and has been rewritten.
     */
    fun getCurrentDatabasePassphrase(): ByteArray? {
        return try {
            val prefs = getEncryptedPrefs()
            val encryptedKeyBase64 = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = prefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Timber.d("No existing database passphrase found")
                return null
            }

            // Decode from Base64 before decrypting
            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            // Decrypt and return using the correct internal function
            val decryptedKey = decryptKey(encryptedKey, iv)

            if (decryptedKey.size == KEY_SIZE_BYTES) {
                Timber.d("Successfully retrieved current passphrase (${decryptedKey.size} bytes)")
                return decryptedKey
            } else {
                Timber.w("Decrypted key is invalid or wrong size: ${decryptedKey.size}")
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error retrieving current passphrase")
            null
        }
    }


    /**
     * ✅ Generate new database passphrase without storing
     */
    fun generateNewDatabasePassphrase(): ByteArray {
        return secureMemoryUtils.generateSecureRandom(KEY_SIZE_BYTES)
    }

    /**
     * ✅ Backup current database key before rotation
     */
    fun backupCurrentDatabaseKey(): Boolean {
        return try {
            val prefs = getEncryptedPrefs()
            val currentEncryptedKey = prefs.getString(ENCRYPTED_KEY_PREF, null)
            val currentIV = prefs.getString(IV_PREF, null)

            if (currentEncryptedKey != null && currentIV != null) {
                // ✅ FIXED: Use commit() properly outside of apply block
                val editor = prefs.edit()
                editor.putString("${ENCRYPTED_KEY_PREF}_backup", currentEncryptedKey)
                editor.putString("${IV_PREF}_backup", currentIV)
                val success = editor.commit()

                if (success) {
                    Timber.i("✅ Current database key backed up")
                } else {
                    Timber.w("⚠️ Backup commit returned false")
                }
                success
            } else {
                Timber.w("No current key to backup")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to backup database key")
            false
        }
    }

    /**
     * ✅ FIXED: Commit new database passphrase to storage
     * ONLY call after successful rekey verification
     */
    fun commitNewDatabasePassphrase(newKey: ByteArray): Boolean {
        return try {
            Timber.i("Encrypting and storing NEW database key...")

            // Backup current key first
            backupCurrentDatabaseKey()

            // Encrypt the new key
            val wrapperKey = getOrCreateWrapperKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)

            val encryptedKey = cipher.doFinal(newKey)
            val iv = cipher.iv

            val encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            // ✅ CRITICAL FIX: Use editor.commit() properly to get Boolean return
            val prefs = getEncryptedPrefs()
            val editor = prefs.edit()
            editor.putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
            editor.putString(IV_PREF, ivBase64)
            val success = editor.commit()  // ✅ commit() returns Boolean

            if (success) {
                Timber.i("✅ New database passphrase committed to storage")
            } else {
                Timber.e("❌ Failed to commit new database passphrase - commit returned false")
            }

            success
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to commit new database passphrase - exception occurred")
            false
        }
    }

    /**
     * ✅ Rollback to backup key
     */
    fun rollbackToBackup(): Boolean {
        return try {
            val prefs = getEncryptedPrefs()
            val backupKey = prefs.getString("${ENCRYPTED_KEY_PREF}_backup", null)
            val backupIV = prefs.getString("${IV_PREF}_backup", null)

            if (backupKey != null && backupIV != null) {
                // ✅ FIXED: Use commit() properly
                val editor = prefs.edit()
                editor.putString(ENCRYPTED_KEY_PREF, backupKey)
                editor.putString(IV_PREF, backupIV)
                val success = editor.commit()

                if (success) {
                    Timber.i("✅ Rolled back to backup database key")
                } else {
                    Timber.e("⚠️ Rollback commit returned false")
                }
                success
            } else {
                Timber.e("❌ No backup available for rollback")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rollback database key")
            false
        }
    }

    /**
     * ✅ Clear backup after successful rotation
     */
    fun clearBackup() {
        try {
            val prefs = getEncryptedPrefs()
            prefs.edit()
                .remove("${ENCRYPTED_KEY_PREF}_backup")
                .remove("${IV_PREF}_backup")
                .apply()
            Timber.d("Backup cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear backup")
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
                Timber.d("No stored key found (first launch)")
                return null
            }

            Timber.d("Found stored encrypted key, attempting to decrypt...")

            val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val decryptedKey = decryptKey(encryptedKey, iv)

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
     */
    private fun generateAndStoreNewKey(): ByteArray {
        Timber.i("Generating NEW 256-bit database encryption key...")

        val databaseKey = secureMemoryUtils.generateSecureRandom(KEY_SIZE_BYTES)
        Timber.d("Generated key size: ${databaseKey.size} bytes")

        try {
            val wrapperKey = getOrCreateWrapperKey()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapperKey)

            val encryptedKey = cipher.doFinal(databaseKey)
            val iv = cipher.iv

            Timber.d("Encrypted key size: ${encryptedKey.size} bytes, IV size: ${iv.size} bytes")

            val encryptedKeyBase64 = Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            val prefs = getEncryptedPrefs()
            // ✅ FIXED: Use commit() for safer synchronous write on first-time initialization
            val success = prefs.edit()
                .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                .putString(IV_PREF, ivBase64)
                .putBoolean(KEY_INITIALIZED_FLAG, true)
                .commit()

            if (!success) {
                Timber.e("❌ CRITICAL: Failed to commit initial database key!")
                throw IllegalStateException("Failed to commit initial database key to storage")
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
     */
    private fun attemptEmergencyKeyRecovery(): ByteArray? {
        Timber.w("🆘 Attempting emergency key recovery...")

        try {
            val regularPrefs = context.getSharedPreferences("database_key_prefs", Context.MODE_PRIVATE)
            val encryptedKeyBase64 = regularPrefs.getString(ENCRYPTED_KEY_PREF, null)
            val ivBase64 = regularPrefs.getString(IV_PREF, null)

            if (encryptedKeyBase64 != null && ivBase64 != null) {
                val encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

                val recoveredKey = decryptKey(encryptedKey, iv)

                Timber.i("✅ Emergency recovery successful!")

                val prefs = getEncryptedPrefs()
                prefs.edit()
                    .putString(ENCRYPTED_KEY_PREF, encryptedKeyBase64)
                    .putString(IV_PREF, ivBase64)
                    .putBoolean(KEY_INITIALIZED_FLAG, true)
                    .apply() // Apply is fine here as it's a recovery, not the primary path

                return recoveredKey
            }

        } catch (e: Exception) {
            Timber.e(e, "Emergency recovery failed")
        }

        Timber.e("❌ All recovery attempts failed - database cannot be accessed")
        return null
    }

    fun requireActiveSession(): Boolean {
        if (!sessionManager.isSessionActive()) {
            Timber.w("No active session - user must log in")
            return false
        }
        return true
    }

    fun clearDatabaseKey() {
        try {
            Timber.w("⚠️ Clearing database key - database will become inaccessible!")

            val prefs = getEncryptedPrefs()
            prefs.edit().clear().apply()

            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }

            context.getSharedPreferences("database_key_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()

            Timber.i("✅ Database key cleared")

        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database key")
        }
    }

    fun isDatabaseKeyInitialized(): Boolean {
        return getEncryptedPrefs().getBoolean(KEY_INITIALIZED_FLAG, false)
    }
}