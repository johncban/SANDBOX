package com.jcb.passbook.security.crypto

import timber.log.Timber
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory

/**
 * ✅ REFACTORED: PasswordEncryptionService with proper CryptoManager API usage
 * 
 * Handles end-to-end encryption for passwords:
 * - Encrypts plaintext passwords with AMK
 * - Decrypts stored passwords
 * - PBKDF2 key derivation
 * - Uses public API methods from CryptoManager
 */
class PasswordEncryptionService(
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val TAG = "PasswordEncryptionService"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val NONCE_LENGTH_BYTES = 12
    }

    /**
     * Encrypt a password with the given AMK
     * 
     * @param plainPassword The plaintext password
     * @param amk The Application Master Key (32 bytes)
     * @return Encrypted data in format: [VERSION | NONCE | CIPHERTEXT | AUTH_TAG]
     */
    fun encryptPassword(plainPassword: String, amk: ByteArray): ByteArray {
        return try {
            Timber.d("Encrypting password...")

            // Generate fresh nonce
            val nonce = ByteArray(NONCE_LENGTH_BYTES)
            SecureRandom().nextBytes(nonce)

            // Initialize cipher
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(amk, 0, amk.size, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            // Encrypt password
            val plainBytes = plainPassword.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plainBytes)

            // Create properly formatted encrypted data
            // ✅ FIXED: Using public API method
            val encryptedData = cryptoManager.createEncryptedData(nonce, ciphertext)

            Timber.d("✓ Password encrypted successfully")
            encryptedData
        } catch (e: Exception) {
            Timber.e(e, "❌ Password encryption failed")
            throw SecurityException("Encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypt a password with the given AMK
     * 
     * @param encryptedData The encrypted password data
     * @param amk The Application Master Key
     * @return Plaintext password
     */
    fun decryptPassword(encryptedData: ByteArray, amk: ByteArray): String {
        return try {
            Timber.d("Decrypting password...")

            // Verify format
            // ✅ FIXED: Using public API method
            if (!cryptoManager.isEncrypted(encryptedData)) {
                throw IllegalArgumentException("Data is not properly encrypted")
            }

            // Extract components
            // ✅ FIXED: Using public API method
            val components = cryptoManager.extractEncryptionComponents(encryptedData)

            // Initialize cipher
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(amk, 0, amk.size, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, components.nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            // Decrypt
            val plainBytes = cipher.doFinal(components.ciphertext)
            val plainPassword = String(plainBytes, Charsets.UTF_8)

            // Clear intermediate data
            plainBytes.fill(0)

            Timber.d("✓ Password decrypted successfully")
            plainPassword
        } catch (e: Exception) {
            Timber.e(e, "❌ Password decryption failed")
            throw SecurityException("Decryption failed: ${e.message}", e)
        }
    }

    /**
     * Verify that password can be decrypted (integrity check)
     */
    fun verifyEncryptedPassword(encryptedData: ByteArray): Boolean {
        return try {
            // ✅ FIXED: Using public API method
            cryptoManager.isEncrypted(encryptedData) &&
            // ✅ FIXED: Using public API method
            cryptoManager.verifyEncryptionFormat(encryptedData)
        } catch (e: Exception) {
            Timber.e(e, "Password verification failed")
            false
        }
    }

    /**
     * Password-Based Key Derivation (PBKDF2)
     * 
     * Implements NIST-recommended PBKDF2-HMAC-SHA256 with:
     * - 600,000 iterations (NIST 2023 recommendation)
     * - SHA-256 hash algorithm
     * - 32-byte output (256 bits) for AES-256
     */
    object PasswordBasedKeyDerivation {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KEY_LENGTH_BITS = 256
        private const val ITERATION_COUNT = 600000  // NIST 2023
        private const val SALT_LENGTH_BYTES = 32

        /**
         * Derive key from password
         * 
         * @param password The user password
         * @param salt Optional salt (generates new if null)
         * @return Pair<derivedKey, salt>
         */
        fun deriveKey(password: String, salt: ByteArray? = null): Pair<ByteArray, ByteArray> {
            return try {
                Timber.d("Deriving key from password...")

                // Use provided salt or generate new
                val usedSalt = salt ?: ByteArray(SALT_LENGTH_BYTES).also {
                    SecureRandom().nextBytes(it)
                }

                // Derive key using PBKDF2
                val keyFactory = SecretKeyFactory.getInstance(ALGORITHM)
                val keySpec: KeySpec = javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(),
                    usedSalt,
                    ITERATION_COUNT,
                    KEY_LENGTH_BITS
                )

                val key = keyFactory.generateSecret(keySpec)
                val derivedKey = key.encoded

                Timber.d("✓ Key derived successfully")
                Pair(derivedKey, usedSalt)
            } catch (e: Exception) {
                Timber.e(e, "❌ Key derivation failed")
                throw SecurityException("Key derivation failed: ${e.message}", e)
            }
        }

        /**
         * Verify password against derived key
         */
        fun verifyPassword(password: String, derivedKey: ByteArray, salt: ByteArray): Boolean {
            return try {
                val (computedKey, _) = deriveKey(password, salt)
                computedKey.contentEquals(derivedKey)
            } catch (e: Exception) {
                Timber.e(e, "Password verification failed")
                false
            }
        }
    }

    /**
     * Utility to generate random initialization vector
     */
    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH_BYTES)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    /**
     * Securely clear sensitive data from ByteArray
     */
    private fun clearBytes(data: ByteArray?) {
        data?.fill(0)
    }

    /**
     * Securely clear sensitive data from CharArray (passwords)
     */
    private fun clearChars(data: CharArray?) {
        data?.fill('\u0000')
    }
}
