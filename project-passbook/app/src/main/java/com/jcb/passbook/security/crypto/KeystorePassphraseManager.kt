// @/app/src/main/java/com/jcb/passbook/security/crypto/KeystorePassphraseManager.kt

package com.jcb.passbook.security.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
object KeystorePassphraseManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // ✅ CRITICAL FIX: Use underscore prefix to avoid Android auto-deletion
    // private const val KEY_ALIAS = "_passbook_database_encryption_key"
    private const val KEY_ALIAS = "jcb.passbook.db.key"


    private const val PREF_NAME = "db_key_prefs"
    private const val ENC_KEY = "enc_pass"
    private const val ENC_KEY_BACKUP = "enc_pass_backup"
    private const val IV_SEPARATOR = "]"
    private const val ROTATION_NEEDED = "rotation_needed"
    private const val LAST_ROTATION_TIME = "last_rotation_ms"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val TAG = "KeystorePassphraseManager"

    /**
     * ✅ CRITICAL: Validate KeyStore key exists before attempting decryption
     */
    fun getOrCreatePassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENC_KEY, null)

        // ✅ CRITICAL: Check if KeyStore key still exists
        if (!keystoreKeyExists()) {
            Timber.tag(TAG).e("❌ KeyStore key missing! This will cause decryption failure.")

            if (encrypted != null) {
                Timber.tag(TAG).e("❌ CRITICAL: Encrypted passphrase exists but KeyStore key is GONE!")
                Timber.tag(TAG).e("❌ CANNOT DECRYPT - Must regenerate key (DATA LOSS)")

                // Clear corrupted state
                prefs.edit().clear().commit()
            }

            // Generate fresh key and passphrase
            return createNewPassphrase(context)
        }

        return if (encrypted != null) {
            try {
                Timber.tag(TAG).d("Decrypting existing passphrase...")
                decryptPassphrase(encrypted)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to decrypt passphrase, attempting backup recovery")
                tryRecoverFromBackup(context) ?: run {
                    Timber.tag(TAG).e("Backup recovery failed, generating new passphrase (DATA LOSS!)")
                    createNewPassphrase(context)
                }
            }
        } else {
            Timber.tag(TAG).i("No existing passphrase found, creating new one")
            createNewPassphrase(context)
        }
    }

    /**
     * ✅ CRITICAL FIX: Get current passphrase WITHOUT creating new one
     * Returns null if no passphrase exists or KeyStore key is missing
     */
    fun getCurrentPassphrase(context: Context): String? {
        // ✅ CRITICAL: Check KeyStore first
        if (!keystoreKeyExists()) {
            Timber.tag(TAG).w("⚠️ KeyStore key does not exist - cannot decrypt passphrase")
            return null
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENC_KEY, null) ?: return null

        return try {
            decryptPassphrase(encrypted)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to decrypt current passphrase")
            null
        }
    }

    /**
     * ✅ NEW: Check if KeyStore key exists
     */
    private fun keystoreKeyExists(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val exists = keyStore.containsAlias(KEY_ALIAS)

            Timber.tag(TAG).d("KeyStore key exists: $exists (alias: $KEY_ALIAS)")
            exists
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking KeyStore")
            false
        }
    }

    fun markRotationNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ROTATION_NEEDED, true).apply()
        Timber.tag(TAG).d("Key rotation marked as needed")
    }

    fun isRotationNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ROTATION_NEEDED, false)
    }

    fun clearRotationFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ROTATION_NEEDED, false)
            .putLong(LAST_ROTATION_TIME, System.currentTimeMillis())
            .apply()
        Timber.tag(TAG).d("Rotation flag cleared")
    }

    fun backupCurrentPassphrase(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val currentEncrypted = prefs.getString(ENC_KEY, null)

            if (currentEncrypted != null) {
                val success = prefs.edit().putString(ENC_KEY_BACKUP, currentEncrypted).commit()
                if (success) {
                    Timber.tag(TAG).d("Current passphrase backed up")
                } else {
                    Timber.tag(TAG).w("Backup commit returned false")
                }
                success
            } else {
                Timber.tag(TAG).w("No current passphrase to backup")
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to backup passphrase")
            false
        }
    }

    fun generateNewPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun commitNewPassphrase(context: Context, newPassphrase: String): Boolean {
        return try {
            val encrypted = encryptPassphrase(newPassphrase)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(ENC_KEY, encrypted).commit()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to commit new passphrase")
            false
        }
    }

    fun clearBackup(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(ENC_KEY_BACKUP).apply()
        Timber.tag(TAG).d("Passphrase backup cleared")
    }

    fun rollbackToBackup(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val backup = prefs.getString(ENC_KEY_BACKUP, null)
        if (backup != null) {
            prefs.edit().putString(ENC_KEY, backup).apply()
            Timber.tag(TAG).i("Rolled back to backup passphrase")
            clearBackup(context)
        } else {
            Timber.tag(TAG).w("No backup found to roll back to")
        }
    }

    private fun tryRecoverFromBackup(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val backupEncrypted = prefs.getString(ENC_KEY_BACKUP, null) ?: return null

        return try {
            val decrypted = decryptPassphrase(backupEncrypted)
            Timber.tag(TAG).i("Successfully recovered passphrase from backup")
            prefs.edit().putString(ENC_KEY, backupEncrypted).apply()
            decrypted
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to decrypt backup passphrase")
            null
        }
    }

    private fun createNewPassphrase(context: Context): String {
        Timber.tag(TAG).i("Creating new database passphrase...")
        val passphrase = generateNewPassphrase()
        val encrypted = encryptPassphrase(passphrase)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val success = prefs.edit().putString(ENC_KEY, encrypted).commit()
        if (success) {
            Timber.tag(TAG).i("✅ New database passphrase created and stored")
        } else {
            Timber.tag(TAG).e("❌ Failed to commit new passphrase to storage!")
        }

        return passphrase
    }

    /**
     * ✅ CRITICAL FIX: Enhanced KeyStore key generation with persistence flags
     */
    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            Timber.tag(TAG).d("✅ Using existing KeyStore key: $KEY_ALIAS")
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        Timber.tag(TAG).i("⚠️ KeyStore key not found, generating new key: $KEY_ALIAS")

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)  // ✅ CRITICAL: No user auth
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGen.init(spec)
        val key = keyGen.generateKey()

        Timber.tag(TAG).i("✅ New KeyStore key generated: $KEY_ALIAS")
        return key
    }

    private fun encryptPassphrase(passphrase: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))

        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        return ivString + IV_SEPARATOR + encryptedString
    }

    private fun decryptPassphrase(encrypted: String): String {
        val parts = encrypted.split(IV_SEPARATOR)
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted data format")

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }
}
