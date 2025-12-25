package com.jcb.passbook.security.crypto

import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PasswordEncryptionService - Encrypts/decrypts password entries using AMK
 *
 * Uses AES-256-GCM for authenticated encryption with associated data (AEAD)
 * ✅ FIPS 140-2 compliant algorithm
 * ✅ Protects against tampering (authentication tag)
 * ✅ Unique IV per encryption operation
 */
@Singleton
class PasswordEncryptionService @Inject constructor(
    private val securityMemoryUtils: SecurityMemoryUtils
) {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val GCM_IV_LENGTH = 12 // bytes (96 bits recommended for GCM)
    }

    /**
     * Encrypt password using AMK (Authentication Master Key)
     *
     * @param password Plaintext password to encrypt
     * @param amk 256-bit AES key from SessionManager
     * @return Encrypted data: [12-byte IV][ciphertext][16-byte auth tag]
     */
    fun encryptPassword(password: String, amk: ByteArray): ByteArray {
        return try {
            require(amk.size == 32) { "AMK must be 256 bits (32 bytes)" }
            require(password.isNotBlank()) { "Password cannot be blank" }

            // Generate unique IV for this encryption
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // Initialize cipher
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(amk, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Encrypt password
            val plaintext = password.toByteArray(Charsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintext)

            // Return: IV || ciphertext (includes auth tag at end)
            iv + ciphertext

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to encrypt password")
            throw SecurityException("Password encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypt password using AMK
     *
     * @param encryptedData Encrypted data from database
     * @param amk 256-bit AES key from SessionManager
     * @return Decrypted plaintext password
     */
    fun decryptPassword(encryptedData: ByteArray, amk: ByteArray): String {
        return try {
            require(amk.size == 32) { "AMK must be 256 bits (32 bytes)" }
            require(encryptedData.size > GCM_IV_LENGTH) { "Invalid encrypted data" }

            // Extract IV and ciphertext
            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)

            // Initialize cipher
            val cipher = Cipher.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(amk, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Decrypt and verify authentication tag
            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext, Charsets.UTF_8)

        } catch (e: javax.crypto.AEADBadTagException) {
            Timber.e(e, "❌ Password authentication failed - data may be tampered")
            throw SecurityException("Password integrity check failed", e)
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to decrypt password")
            throw SecurityException("Password decryption failed: ${e.message}", e)
        }
    }

    /**
     * Securely wipe password bytes from memory
     */
    fun wipePassword(passwordBytes: ByteArray) {
        securityMemoryUtils.secureWipe(passwordBytes)
    }
}
