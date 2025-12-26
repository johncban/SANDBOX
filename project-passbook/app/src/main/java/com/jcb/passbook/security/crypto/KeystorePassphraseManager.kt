package com.jcb.passbook.security.crypto

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.SecureRandom

class KeystorePassphraseManager(
    private val context: Context
) {
    companion object {
        private const val KEYSTORE_ALIAS = "passbook_master_key"
        private const val PREFS_NAME = "passbook_secure_prefs"
        private const val KEY_PASSPHRASE = "database_passphrase"
        private const val PASSPHRASE_LENGTH = 32 // 256 bits
    }

    /**
     * ✅ NEW: Get or generate database passphrase
     * This is called by DatabaseModule during initialization
     */
    fun getPassphrase(): ByteArray? {
        return try {
            // Try to retrieve existing passphrase
            val storedPassphrase = retrievePassphrase(activity)
            if (storedPassphrase != null) {
                Timber.d("✓ Retrieved existing passphrase")
                return storedPassphrase
            }

            // Generate new passphrase if none exists
            val newPassphrase = generatePassphrase()
            storePassphrase(newPassphrase)
            Timber.i("✅ Generated and stored new passphrase")
            newPassphrase

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to get passphrase")
            null
        }
    }

    /**
     * Retrieve stored passphrase from EncryptedSharedPreferences
     */
    fun retrievePassphrase(activity: FragmentActivity): ByteArray? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val passphraseHex = sharedPreferences.getString(KEY_PASSPHRASE, null)
            passphraseHex?.let { hexStringToByteArray(it) }

        } catch (e: Exception) {
            Timber.e(e, "Error retrieving passphrase")
            null
        }
    }

    /**
     * Generate a new random passphrase
     */
    private fun generatePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LENGTH)
        SecureRandom().nextBytes(passphrase)
        return passphrase
    }

    /**
     * Store passphrase in EncryptedSharedPreferences
     */
    private fun storePassphrase(passphrase: ByteArray) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPreferences.edit()
            .putString(KEY_PASSPHRASE, byteArrayToHexString(passphrase))
            .apply()
    }

    /**
     * Convert byte array to hex string
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert hex string to byte array
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    /**
     * Delete stored passphrase (use during app reset/uninstall)
     */
    fun deletePassphrase() {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            sharedPreferences.edit().remove(KEY_PASSPHRASE).apply()
            Timber.i("✅ Passphrase deleted")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting passphrase")
        }
    }
}
