package com.jcb.passbook.security.crypto

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.SecureRandom

/**
 * ✅ REFACTORED: KeystorePassphraseManager with all required methods
 * 
 * Responsibilities:
 * 1. Generate and store database passphrase (no activity needed)
 * 2. Retrieve passphrase for database initialization
 * 3. Derive AMK (Application Master Key) from passphrase for session
 * 4. Support both activity-based (biometric) and activity-free retrieval
 */
class KeystorePassphraseManager(private val context: Context) {
    companion object {
        private const val TAG = "KeystorePassphraseManager"
        private const val PREFS_NAME = "passbook_keystore_prefs"
        private const val KEY_PASSPHRASE = "db_passphrase"
        private const val KEY_SALT = "db_salt"
        private const val PASSPHRASE_LENGTH_BYTES = 32
    }

    private val prefLock = Any()

    /**
     * Get or generate database passphrase (NO activity required)
     * 
     * This is called during DI/Database initialization:
     * 1. Checks EncryptedSharedPreferences for existing passphrase
     * 2. If not found, generates new random passphrase
     * 3. Stores securely using EncryptedSharedPreferences
     * 4. Returns passphrase for SQLCipher initialization
     * 
     * ✅ Safe to call from any thread during app startup
     * ✅ Does not require FragmentActivity
     */
    fun getPassphrase(): ByteArray? {
        synchronized(prefLock) {
            return try {
                Timber.d("Retrieving or generating database passphrase...")

                // Try to retrieve existing passphrase
                val storedHex = getStoredPassphrase()
                if (storedHex != null) {
                    Timber.d("✓ Retrieved existing passphrase from secure storage")
                    return hexStringToByteArray(storedHex)
                }

                // Generate new passphrase
                Timber.i("Generating new database passphrase...")
                val newPassphrase = generatePassphrase()
                
                // Store securely
                storePassphrase(newPassphrase)
                
                Timber.i("✓ Generated and stored new database passphrase")
                newPassphrase
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to get or generate passphrase")
                null
            }
        }
    }

    /**
     * Retrieve passphrase WITH activity (for future biometric support)
     * 
     * This is called when user authenticates during session start:
     * 1. Accepts FragmentActivity for potential biometric prompt
     * 2. Could implement biometric/PIN verification here
     * 3. Currently delegates to secure storage retrieval
     * 
     * Design allows for future biometric enhancement:
     * ```
     * if (showBiometricPrompt(activity)) {
     *     return biometricEncryptedPassphrase
     * }
     * ```
     */
    fun retrievePassphrase(activity: FragmentActivity): ByteArray? {
        synchronized(prefLock) {
            return try {
                Timber.d("Retrieving passphrase with activity context...")
                
                // TODO: Future enhancement - biometric/PIN verification
                // For now, retrieve from secure storage
                val hex = getStoredPassphrase()
                if (hex != null) {
                    Timber.d("✓ Retrieved passphrase for session")
                    return hexStringToByteArray(hex)
                }

                Timber.w("Passphrase not found in secure storage")
                null
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to retrieve passphrase")
                null
            }
        }
    }

    /**
     * Derive Application Master Key (AMK) from passphrase
     * 
     * PBKDF2 with NIST 2023 recommended 600,000 iterations
     * Used to derive AMK for password encryption/decryption
     * 
     * @param passphrase The database passphrase (cleared after use)
     * @return AMK (32 bytes) for AES-256 key derivation
     */
    fun deriveAMK(passphrase: ByteArray): ByteArray {
        return try {
            Timber.d("Deriving AMK from passphrase...")

            val amk = PasswordEncryptionService.PasswordBasedKeyDerivation.deriveKey(
                String(passphrase, Charsets.UTF_8),
                salt = null  // Use or generate salt as needed
            ).first

            // Clear passphrase from memory
            passphrase.fill(0)

            Timber.d("✓ AMK derived successfully")
            amk
        } catch (e: Exception) {
            Timber.e(e, "❌ AMK derivation failed")
            throw SecurityException("AMK derivation failed: ${e.message}", e)
        }
    }

    /**
     * Derive AMK with specific salt (for verification/recovery scenarios)
     */
    fun deriveAMKWithSalt(passphrase: ByteArray, salt: ByteArray): ByteArray {
        return try {
            Timber.d("Deriving AMK with provided salt...")

            val amk = PasswordEncryptionService.PasswordBasedKeyDerivation.deriveKey(
                String(passphrase, Charsets.UTF_8),
                salt = salt
            ).first

            passphrase.fill(0)
            Timber.d("✓ AMK derived with salt")
            amk
        } catch (e: Exception) {
            Timber.e(e, "❌ AMK derivation with salt failed")
            throw SecurityException("AMK derivation failed: ${e.message}", e)
        }
    }

    // ========== Private Helpers ==========

    /**
     * Retrieve passphrase from EncryptedSharedPreferences
     * Returns: hex string (or null if not exists)
     */
    private fun getStoredPassphrase(): String? {
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

            sharedPreferences.getString(KEY_PASSPHRASE, null)
        } catch (e: Exception) {
            Timber.e(e, "Error reading passphrase from prefs")
            null
        }
    }

    /**
     * Generate new random passphrase (32 bytes = 256 bits)
     */
    private fun generatePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES)
        SecureRandom().nextBytes(passphrase)
        return passphrase
    }

    /**
     * Store passphrase in EncryptedSharedPreferences
     */
    private fun storePassphrase(passphrase: ByteArray) {
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

            val hex = byteArrayToHexString(passphrase)
            sharedPreferences.edit().apply {
                putString(KEY_PASSPHRASE, hex)
                apply()
            }

            Timber.d("✓ Passphrase stored securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to store passphrase")
            throw SecurityException("Failed to store passphrase: ${e.message}", e)
        }
    }

    /**
     * Convert ByteArray to hex string for storage
     */
    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Convert hex string back to ByteArray
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
