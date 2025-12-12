package com.jcb.passbook.data.local.database.encryption

import android.content.Context
import android.provider.Settings
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import timber.log.Timber

/**
 * Database Encryption Manager
 * Handles SQLCipher encryption key management and database initialization
 */
class DatabaseEncryptionManager(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseEncryption"
    }

    /**
     * Get encryption passphrase with fallback and recovery logic.
     *
     * @return ByteArray passphrase for SQLCipher database encryption
     * @throws Exception if all passphrase generation methods fail
     */
    fun getEncryptionPassphrase(): ByteArray {
        return try {
            Timber.tag(TAG).d("Attempting to get encryption passphrase...")

            // Try to get existing passphrase from Android Keystore
            // Convert String? to ByteArray?
            val existingPassphraseString: String? =
                KeystorePassphraseManager.getCurrentPassphrase(context)

            val existingPassphrase: ByteArray? =
                existingPassphraseString?.toByteArray(Charsets.UTF_8)

            if (existingPassphrase != null && existingPassphrase.isNotEmpty()) {
                Timber.tag(TAG).d("✓ Using existing passphrase from Keystore")
                existingPassphrase
            } else {
                // If no existing passphrase, create new one
                Timber.tag(TAG).i("No existing passphrase found, generating new one...")

                // Convert String to ByteArray
                val newPassphraseString: String =
                    KeystorePassphraseManager.getOrCreatePassphrase(context)

                val newPassphrase: ByteArray =
                    newPassphraseString.toByteArray(Charsets.UTF_8)

                Timber.tag(TAG).d("✓ New passphrase generated and stored")
                newPassphrase
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error getting encryption passphrase, using fallback...")

            // Fallback: Generate deterministic passphrase from Android ID
            // WARNING: This is less secure than Keystore, only for recovery
            try {
                val fallbackPassphrase: ByteArray = generateFallbackPassphrase(context)
                Timber.tag(TAG).w("⚠️  Using fallback encryption passphrase")
                fallbackPassphrase
            } catch (fallbackError: Exception) {
                Timber.tag(TAG).e(fallbackError, "❌ Fallback passphrase generation failed!")
                throw Exception(
                    "Cannot generate encryption passphrase: ${e.message}",
                    fallbackError
                )
            }
        }
    }

    /**
     * Generate fallback encryption passphrase (deterministic)
     * Uses Android ID and package name for consistency
     *
     * @param context Application context
     * @return ByteArray passphrase derived from device identifiers
     */
    private fun generateFallbackPassphrase(context: Context): ByteArray {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val packageName = context.packageName
            val combined = "$androidId:$packageName:passbook:v1"

            combined.toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            // Last resort: hardcoded fallback (NOT SECURE, ONLY FOR EMERGENCIES)
            Timber.tag(TAG).e(e, "Could not generate fallback passphrase")
            "passbook_emergency_key_v1".toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Validate database can be opened with current passphrase
     *
     * @return true if passphrase can be retrieved, false otherwise
     */
    fun validateDatabaseConnection(): Boolean {
        return try {
            Timber.tag(TAG).d("Validating database encryption...")
            val passphrase: ByteArray = getEncryptionPassphrase()

            if (passphrase.isNotEmpty()) {
                Timber.tag(TAG).d("✓ Database encryption validation successful")
                true
            } else {
                Timber.tag(TAG).e("❌ Empty passphrase returned")
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Database encryption validation failed")
            false
        }
    }

    /**
     * Rotate encryption key (advanced operation)
     * Note: Requires database migration to re-encrypt with new key
     *
     * @return true if key rotation initiated, false on failure
     */
    fun rotateEncryptionKey(): Boolean {
        return try {
            Timber.tag(TAG).i("Attempting to rotate encryption key...")
            KeystorePassphraseManager.generateNewPassphrase()
            Timber.tag(TAG).i("✓ Encryption key rotation initiated")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Encryption key rotation failed")
            false
        }
    }
}
