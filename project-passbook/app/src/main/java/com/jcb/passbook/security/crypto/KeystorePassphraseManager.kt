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
    private const val KEY_ALIAS = "passbook_db_key"
    private const val PREF_NAME = "db_key_prefs"
    private const val ENC_KEY = "enc_pass"
    private const val ENC_KEY_BACKUP = "enc_pass_backup"

    // ✅ Track rotation state
    private const val ROTATION_NEEDED = "rotation_needed"
    private const val LAST_ROTATION_TIME = "last_rotation_ms"

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
     * ✅ Get current passphrase WITHOUT creating new one
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
     * ✅ Mark that key rotation is needed on next login
     */
    fun markRotationNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(ROTATION_NEEDED, true).apply()
        Timber.d("🔄 Key rotation marked as needed")
    }

    /**
     * ✅ Check if rotation is needed
     */
    fun isRotationNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(ROTATION_NEEDED, false)
    }

    /**
     * ✅ PUBLIC: Clear rotation flag after successful rotation or initialization
     */
    fun clearRotationFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ROTATION_NEEDED, false)
            .putLong(LAST_ROTATION_TIME, System.currentTimeMillis())
            .apply()
        Timber.d("✅ Rotation flag cleared")
    }

    /**
     * ✅ Create backup of current passphrase before rotation
     */
    private fun backupCurrentPassphrase(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val currentEncrypted = prefs.getString(ENC_KEY, null)

            if (currentEncrypted != null) {
                prefs.edit().putString(ENC_KEY_BACKUP, currentEncrypted).commit()
                Timber.d("✅ Current passphrase backed up")
                true
            } else {
                Timber.w("No current passphrase to backup")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to backup passphrase")
            false
        }
    }

    /**
     * ✅ Restore passphrase from backup (rollback mechanism)
     */
    fun rollbackToBackup(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val backup = prefs.getString(ENC_KEY_BACKUP, null)

            if (backup != null) {
                prefs.edit().putString(ENC_KEY, backup).commit()
                Timber.i("✅ Rolled back to backup passphrase")
                true
            } else {
                Timber.e("No backup available for rollback")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rollback passphrase")
            false
        }
    }

    /**
     * ✅ Generate and return new passphrase WITHOUT storing it
     */
    fun generateNewPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * ✅ Commit new passphrase to storage (call ONLY after rekey success)
     */
    fun commitNewPassphrase(context: Context, newPassphrase: String): Boolean {
        return try {
            // First backup current passphrase
            backupCurrentPassphrase(context)

            // Encrypt and store new passphrase
            val encryptedPass = encryptPassphrase(newPassphrase)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val success = prefs.edit().putString(ENC_KEY, encryptedPass).commit()

            if (success) {
                Timber.i("✅ New passphrase committed to storage")
                clearRotationFlag(context)
            } else {
                Timber.e("❌ Failed to commit new passphrase")
            }

            success
        } catch (e: Exception) {
            Timber.e(e, "Failed to commit new passphrase")
            false
        }
    }

    /**
     * ✅ Clear backup after successful rotation
     */
    fun clearBackup(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(ENC_KEY_BACKUP).apply()
            Timber.d("Backup cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear backup")
        }
    }

    /**
     * Try to recover from backup
     */
    private fun tryRecoverFromBackup(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val backup = prefs.getString(ENC_KEY_BACKUP, null)

            if (backup != null) {
                val recovered = decryptPassphrase(backup)
                Timber.i("✅ Recovered passphrase from backup")
                // Restore as current
                prefs.edit().putString(ENC_KEY, backup).commit()
                recovered
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Backup recovery failed")
            null
        }
    }

    /**
     * Create new passphrase and store it
     */
    private fun createNewPassphrase(context: Context): String {
        val newPass = generateNewPassphrase()
        val encryptedPass = encryptPassphrase(newPass)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ENC_KEY, encryptedPass).commit()
        Timber.i("✅ New passphrase created and stored")
        return newPass
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
            .setUserAuthenticationRequired(false)
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
