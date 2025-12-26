package com.jcb.passbook.security.crypto

import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager - Secure encryption/decryption with AES-256-GCM
 *
 * Features:
 * - AES-256 encryption with Galois/Counter Mode (GCM)
 * - Authenticated encryption with associated data (AEAD)
 * - Metadata-based encryption detection (no string prefix)
 * - Version byte for future compatibility
 * - Thread-safe operations
 * - Secure memory handling via SecureMemoryUtils
 *
 * Format: [VERSION(1) | NONCE(12) | CIPHERTEXT | AUTH_TAG(16)]
 */
class CryptoManager(
    private val secureMemoryUtils: SecureMemoryUtils
) {

    companion object {
        private const val TAG = "CryptoManager"

        // Encryption constants
        private const val ENCRYPTION_VERSION: Byte = 0x01
        private const val AES_KEY_SIZE = 256
        private const val GCM_NONCE_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val MIN_ENCRYPTED_SIZE = 1 + GCM_NONCE_LENGTH + GCM_TAG_LENGTH // 29 bytes

        // Cipher specifications
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"

        /**
         * Check if data is properly encrypted
         *
         * Returns true ONLY if:
         * 1. Data is not null and has minimum length
         * 2. First byte matches ENCRYPTION_VERSION (0x01)
         *
         * This prevents double encryption and handles edge cases correctly
         */
        fun isEncrypted(data: ByteArray?): Boolean {
            if (data == null || data.isEmpty()) return false

            // Check minimum size
            if (data.size < MIN_ENCRYPTED_SIZE) {
                return false
            }

            // Check version byte
            return data[0] == ENCRYPTION_VERSION
        }
    }

    /**
     * Encrypt plaintext data using AES-256-GCM
     *
     * @param plaintext Raw data to encrypt
     * @param key Secret key for encryption
     * @return Encrypted data with format: [VERSION | NONCE | CIPHERTEXT | TAG]
     * @throws IllegalArgumentException if inputs are invalid
     * @throws Exception if encryption fails
     */
    @Synchronized
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
        require(key.encoded.size == AES_KEY_SIZE / 8) { "Key must be 256 bits (32 bytes)" }

        var nonce: ByteArray? = null
        var cipher: Cipher? = null

        try {
            Log.d(TAG, "🔒 Encrypting ${plaintext.size} bytes")

            // Generate secure random nonce
            nonce = ByteArray(GCM_NONCE_LENGTH)
            SecureRandom().nextBytes(nonce)

            // Initialize cipher with GCM mode
            cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

            // Encrypt data (includes authentication tag)
            val ciphertext = cipher.doFinal(plaintext)

            // Create formatted output: [VERSION | NONCE | CIPHERTEXT]
            val result = createEncryptedData(nonce, ciphertext)

            Log.d(TAG, "✅ Encryption successful: ${result.size} bytes")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Encryption failed: ${e.message}", e)
            throw SecurityException("Encryption failed", e)
        } finally {
            // Secure cleanup
            nonce?.let { secureMemoryUtils.wipeByteArray(it) }
        }
    }

    /**
     * Decrypt encrypted data using AES-256-GCM
     *
     * @param encryptedData Encrypted data with format: [VERSION | NONCE | CIPHERTEXT | TAG]
     * @param key Secret key for decryption
     * @return Decrypted plaintext
     * @throws IllegalArgumentException if data is not properly encrypted
     * @throws Exception if decryption or authentication fails
     */
    @Synchronized
    fun decrypt(encryptedData: ByteArray, key: SecretKey): ByteArray {
        require(isEncrypted(encryptedData)) { "Data is not properly encrypted" }
        require(key.encoded.size == AES_KEY_SIZE / 8) { "Key must be 256 bits (32 bytes)" }

        var nonce: ByteArray? = null
        var cipher: Cipher? = null

        try {
            Log.d(TAG, "🔓 Decrypting ${encryptedData.size} bytes")

            // Extract encryption components
            val components = extractEncryptionComponents(encryptedData)
            nonce = components.nonce
            val ciphertext = components.ciphertext

            // Initialize cipher for decryption
            cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            // Decrypt and verify authentication tag
            val plaintext = cipher.doFinal(ciphertext)

            Log.d(TAG, "✅ Decryption successful: ${plaintext.size} bytes")
            return plaintext

        } catch (e: Exception) {
            Log.e(TAG, "❌ Decryption failed: ${e.message}", e)
            throw SecurityException("Decryption or authentication failed", e)
        } finally {
            // Secure cleanup
            nonce?.let { secureMemoryUtils.wipeByteArray(it) }
        }
    }

    /**
     * Extract encryption components from encrypted data
     *
     * Format: [VERSION(1) | NONCE(12) | CIPHERTEXT | TAG(16)]
     *
     * @param encryptedData Formatted encrypted data
     * @return EncryptionComponents containing nonce and ciphertext
     * @throws IllegalArgumentException if data is not properly encrypted
     */
    private fun extractEncryptionComponents(encryptedData: ByteArray): EncryptionComponents {
        if (!isEncrypted(encryptedData)) {
            throw IllegalArgumentException("Data is not properly encrypted")
        }

        try {
            // Skip version byte (index 0)
            val nonceOffset = 1

            // Extract nonce (12 bytes)
            val nonce = ByteArray(GCM_NONCE_LENGTH)
            System.arraycopy(encryptedData, nonceOffset, nonce, 0, GCM_NONCE_LENGTH)

            // Extract ciphertext (everything after nonce, includes GCM tag)
            val ciphertextOffset = nonceOffset + GCM_NONCE_LENGTH
            val ciphertextLength = encryptedData.size - ciphertextOffset
            val ciphertext = ByteArray(ciphertextLength)
            System.arraycopy(encryptedData, ciphertextOffset, ciphertext, 0, ciphertextLength)

            return EncryptionComponents(nonce, ciphertext)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to extract encryption components: ${e.message}", e)
            throw IllegalArgumentException("Invalid encrypted data format", e)
        }
    }

    /**
     * Create formatted encrypted data with metadata
     *
     * Format: [VERSION | NONCE | CIPHERTEXT | TAG]
     *
     * @param nonce Random nonce (12 bytes)
     * @param ciphertext Encrypted data including GCM authentication tag
     * @return Formatted encrypted data
     */
    private fun createEncryptedData(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        require(nonce.size == GCM_NONCE_LENGTH) { "Nonce must be $GCM_NONCE_LENGTH bytes" }
        require(ciphertext.isNotEmpty()) { "Ciphertext cannot be empty" }

        val result = ByteArray(1 + nonce.size + ciphertext.size)

        // Set version byte
        result[0] = ENCRYPTION_VERSION

        // Copy nonce
        System.arraycopy(nonce, 0, result, 1, nonce.size)

        // Copy ciphertext (includes GCM tag)
        System.arraycopy(ciphertext, 0, result, 1 + nonce.size, ciphertext.size)

        return result
    }

    /**
     * Generate a new AES-256 encryption key
     *
     * @return SecretKey for AES-256 encryption
     */
    fun generateKey(): SecretKey {
        try {
            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM)
            keyGenerator.init(AES_KEY_SIZE, SecureRandom())
            val key = keyGenerator.generateKey()

            Log.d(TAG, "✅ Generated new AES-256 key")
            return key

        } catch (e: Exception) {
            Log.e(TAG, "❌ Key generation failed: ${e.message}", e)
            throw SecurityException("Failed to generate encryption key", e)
        }
    }

    /**
     * Create SecretKey from raw bytes
     *
     * @param keyBytes Raw key material (must be 32 bytes for AES-256)
     * @return SecretKey
     */
    fun createKeyFromBytes(keyBytes: ByteArray): SecretKey {
        require(keyBytes.size == AES_KEY_SIZE / 8) { "Key must be 256 bits (32 bytes)" }
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * Securely wipe key from memory
     *
     * @param key SecretKey to wipe
     */
    fun wipeKey(key: SecretKey?) {
        key?.encoded?.let { secureMemoryUtils.wipeByteArray(it) }
    }

    /**
     * Data class holding encryption components
     */
    data class EncryptionComponents(
        val nonce: ByteArray,
        val ciphertext: ByteArray // Includes GCM authentication tag
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptionComponents
            if (!nonce.contentEquals(other.nonce)) return false
            if (!ciphertext.contentEquals(other.ciphertext)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }

        override fun toString(): String {
            return "EncryptionComponents(nonceSize=${nonce.size}, ciphertextSize=${ciphertext.size})"
        }
    }
}
