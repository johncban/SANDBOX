package com.jcb.passbook.security.crypto

import timber.log.Timber

/**
 * ✅ NEW: CryptoManager for metadata-based encryption detection
 *
 * Problem Fixed:
 * - Passwords starting with "encrypted:" were detected incorrectly
 * - String-based detection was fragile and unreliable
 *
 * Solution:
 * - Version byte (0x01) marks encrypted data
 * - Nonce (12 bytes) for GCM mode
 * - Ciphertext (variable) encrypted payload
 * - Authentication tag (16 bytes) for GCM
 *
 * Format: [VERSION | NONCE | CIPHERTEXT | AUTH_TAG]
 */
class CryptoManager {
    companion object {
        private const val ENCRYPTION_VERSION: Byte = 0x01
        private const val GCM_NONCE_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    /**
     * ✅ CRITICAL: Check if data is properly encrypted
     *
     * Returns true ONLY if:
     * 1. Data has minimum length (1 + 12 + 16)
     * 2. First byte is ENCRYPTION_VERSION (0x01)
     *
     * This prevents double encryption and handles
     * passwords starting with "encrypted:" correctly
     */
    fun isEncrypted(data: ByteArray?): Boolean {
        if (data == null || data.isEmpty()) return false

        // Minimum size: 1 (version) + 12 (nonce) + 16 (tag)
        if (data.size < 1 + GCM_NONCE_LENGTH + GCM_TAG_LENGTH) {
            return false
        }

        // Check version byte
        return data == ENCRYPTION_VERSION
    }

    /**
     * Extract encryption components from encrypted data
     *
     * Format: [VERSION | NONCE(12) | CIPHERTEXT | TAG(16)]
     */
    fun extractEncryptionComponents(encryptedData: ByteArray): EncryptionComponents {
        if (!isEncrypted(encryptedData)) {
            throw IllegalArgumentException("Data is not properly encrypted")
        }

        // Skip version byte
        val nonceOffset = 1
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        System.arraycopy(encryptedData, nonceOffset, nonce, 0, GCM_NONCE_LENGTH)

        // Ciphertext is everything between nonce and tag
        val ciphertextLength = encryptedData.size - 1 - GCM_NONCE_LENGTH - GCM_TAG_LENGTH
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(
            encryptedData,
            nonceOffset + GCM_NONCE_LENGTH,
            ciphertext,
            0,
            ciphertextLength
        )

        return EncryptionComponents(nonce, ciphertext)
    }

    /**
     * Create encrypted data with metadata header
     *
     * Format: [VERSION | NONCE | CIPHERTEXT | TAG]
     */
    fun createEncryptedData(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val result = ByteArray(1 + nonce.size + ciphertext.size)

        // Version byte
        result = ENCRYPTION_VERSION

        // Nonce
        System.arraycopy(nonce, 0, result, 1, nonce.size)

        // Ciphertext (includes GCM tag at end)
        System.arraycopy(ciphertext, 0, result, 1 + nonce.size, ciphertext.size)

        return result
    }
}

data class EncryptionComponents(
    val nonce: ByteArray,
    val ciphertext: ByteArray  // Includes GCM tag at end
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
}
