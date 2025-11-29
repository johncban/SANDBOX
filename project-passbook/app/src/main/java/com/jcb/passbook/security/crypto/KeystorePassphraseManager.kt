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
    private const val KEY_ALIAS = "passbook_db_key"
    private const val PREF_NAME = "db_key_prefs"
    private const val ENC_KEY = "enc_pass"
    private const val ENC_KEY_BACKUP = "enc_pass_backup"
    private const val IV_SEPARATOR = "]"

    // Track rotation state
    private const val ROTATION_NEEDED = "rotation_needed"
    private const val LAST_ROTATION_TIME = "last_rotation_ms"

    private const val GCM_IV_LENGTH = 12 // 96 bits
    private const val GCM_TAG_LENGTH = 128 // bits

    /**
     * Get or create the current database passphrase
     */
    fun getOrCreatePassphrase(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENC_KEY, null)

        return if (encrypted != null) {
            try {
                decryptPassphrase(encrypted)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decrypt passphrase, attempting backup recovery")
                tryRecoverFromBackup(context) ?: run {
                    Timber.e("Backup recovery failed, generating new passphrase (DATA LOSS!)")
                    createNewPassphrase(context)
                }
            }
        } else {
            createNewPassphrase(context)
        }
    }

    /**
     * CRITICAL FIX: Get current passphrase WITHOUT creating new one
     * Returns null if no passphrase exists yet (first launch scenario)
     */
    fun getCurrentPassphrase(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENC_KEY, null) ?: return null

        return try {
            decryptPassphrase(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt current passphrase")
            null
        }
    }

    /**
     * Mark that key rotation is needed on next login
     */
    fun markRotationNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ROTATION_NEEDED, true).apply()
        Timber.d("Key rotation marked as needed")
    }

    /**
     * Check if rotation is needed
     */
    fun isRotationNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ROTATION_NEEDED, false)
    }

    /**
     * PUBLIC: Clear rotation flag after successful rotation or initialization
     */
    fun clearRotationFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ROTATION_NEEDED, false)
            .putLong(LAST_ROTATION_TIME, System.currentTimeMillis())
            .apply()
        Timber.d("Rotation flag cleared")
    }

    /**
     * CRITICAL FIX: Create backup of current passphrase before rotation
     * Returns false if there's no current passphrase to backup
     */
    fun backupCurrentPassphrase(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val currentEncrypted = prefs.getString(ENC_KEY, null)

            if (currentEncrypted != null) {
                val success = prefs.edit().putString(ENC_KEY_BACKUP, currentEncrypted).commit()
                if (success) {
                    Timber.d("Current passphrase backed up")
                } else {
                    Timber.w("Backup commit returned false")
                }
                success
            } else {
                Timber.w("No current passphrase to backup")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to backup passphrase")
            false
        }
    }

    // --- ADDED MISSING PUBLIC FUNCTIONS ---

    /**
     * Generates a new cryptographically secure passphrase.
     */
    fun generateNewPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32) // 256 bits of entropy
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Encrypts and commits the new passphrase as the primary key.
     */
    fun commitNewPassphrase(context: Context, newPassphrase: String): Boolean {
        return try {
            val encrypted = encryptPassphrase(newPassphrase)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(ENC_KEY, encrypted).commit()
        } catch (e: Exception) {
            Timber.e(e, "Failed to commit new passphrase")
            false
        }
    }

    /**
     * Clears the backup passphrase from storage after a successful rotation.
     */
    fun clearBackup(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(ENC_KEY_BACKUP).apply()
        Timber.d("Passphrase backup cleared")
    }

    /**
     * Rolls back to the backup passphrase if the rotation fails.
     */
    fun rollbackToBackup(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val backup = prefs.getString(ENC_KEY_BACKUP, null)
        if (backup != null) {
            prefs.edit().putString(ENC_KEY, backup).apply()
            Timber.i("Rolled back to backup passphrase")
            clearBackup(context)
        } else {
            Timber.w("No backup found to roll back to")
        }
    }


    // --- ADDED MISSING PRIVATE HELPER FUNCTIONS ---

    private fun tryRecoverFromBackup(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val backupEncrypted = prefs.getString(ENC_KEY_BACKUP, null) ?: return null

        return try {
            val decrypted = decryptPassphrase(backupEncrypted)
            Timber.i("Successfully recovered passphrase from backup")
            // Restore backup as the main key
            prefs.edit().putString(ENC_KEY, backupEncrypted).apply()
            decrypted
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt backup passphrase")
            null
        }
    }

    private fun createNewPassphrase(context: Context): String {
        val passphrase = generateNewPassphrase()
        val encrypted = encryptPassphrase(passphrase)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ENC_KEY, encrypted).apply()
        Timber.i("New database passphrase created and stored")
        return passphrase
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

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
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun encryptPassphrase(passphrase: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))

        // Prepend IV to the ciphertext for storage
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