package com.jcb.passbook.security.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * ✅ REFACTORED: CryptoManager with complete public API
 * 
 * Manages all encryption/decryption operations for PassBook
 * - AES-256-GCM encryption (NIST approved)
 * - Keystore integration for key material
 * - Secure nonce generation
 * - Public API for encryption component extraction and validation
 */
open class CryptoManager(
    private val secureMemoryUtils: SecureMemoryUtils
) {
    companion object {
        private const val TAG = "CryptoManager"
        private const val KEYSTORE_ALIAS = "passbook_main_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val NONCE_LENGTH_BYTES = 12
        private const val ENCRYPTION_VERSION: Byte = 0x01
    }

    private var keyStore: KeyStore? = null
    private val cipherLock = Any()

    /**
     * Initialize AndroidKeyStore and generate master key if needed
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun initializeKeystore() {
        synchronized(cipherLock) {
            try {
                keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore!!.load(null)

                // Generate master key if not exists
                if (!keyStore!!.containsAlias(KEYSTORE_ALIAS)) {
                    generateMasterKey()
                }

                Timber.i("✅ Keystore initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "❌ Keystore initialization failed")
                throw SecurityException("Keystore init failed: ${e.message}", e)
            }
        }
    }

    /**
     * Generate master key in AndroidKeyStore (hardware-backed if available)
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun generateMasterKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val keySpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)  // AES-256
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setIsStrongBoxBacked(true)  // Use StrongBox if available
                .build()

            keyGenerator.init(keySpec)
            keyGenerator.generateKey()

            Timber.i("✅ Master key generated in AndroidKeyStore")
        } catch (e: Exception) {
            Timber.e(e, "❌ Master key generation failed")
            throw SecurityException("Master key generation failed: ${e.message}", e)
        }
    }

    /**
     * Encrypt data with AMK (Application Master Key)
     * 
     * Returns: [VERSION | NONCE | CIPHERTEXT | AUTH_TAG]
     */
    fun encryptData(plaintext: ByteArray, amk: SecretKeySpec): ByteArray {
        synchronized(cipherLock) {
            return try {
                // Generate fresh nonce
                val nonce = ByteArray(NONCE_LENGTH_BYTES)
                SecureRandom().nextBytes(nonce)

                // Initialize cipher
                val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
                cipher.init(Cipher.ENCRYPT_MODE, amk, gcmSpec)

                // Encrypt
                val ciphertext = cipher.doFinal(plaintext)

                // Return: VERSION | NONCE | CIPHERTEXT
                val version = byteArrayOf(ENCRYPTION_VERSION)
                version + nonce + ciphertext
            } catch (e: Exception) {
                Timber.e(e, "❌ Encryption failed")
                throw SecurityException("Encryption failed: ${e.message}", e)
            }
        }
    }

    /**
     * Decrypt data with AMK
     */
    fun decryptData(encryptedData: ByteArray, amk: SecretKeySpec): ByteArray {
        synchronized(cipherLock) {
            return try {
                // Extract components
                val components = extractEncryptionComponents(encryptedData)

                // Initialize cipher
                val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, components.nonce)
                cipher.init(Cipher.DECRYPT_MODE, amk, gcmSpec)

                // Decrypt
                cipher.doFinal(components.ciphertext)
            } catch (e: Exception) {
                Timber.e(e, "❌ Decryption failed")
                throw SecurityException("Decryption failed: ${e.message}", e)
            }
        }
    }

    // ========== PUBLIC API for Encryption Operations ==========

    /**
     * Check if data is properly encrypted with our format
     * Format: [VERSION | NONCE | CIPHERTEXT | AUTH_TAG]
     */
    fun isEncrypted(data: ByteArray): Boolean {
        return data.isNotEmpty() &&
               data[0] == ENCRYPTION_VERSION &&
               data.size > (1 + NONCE_LENGTH_BYTES)  // At least version + nonce
    }

    /**
     * Extract encryption components from encrypted data
     * Returns: (version, nonce, ciphertext)
     */
    fun extractEncryptionComponents(encryptedData: ByteArray): EncryptionComponents {
        if (!isEncrypted(encryptedData)) {
            throw IllegalArgumentException(
                "Data is not properly encrypted. " +
                "Expected format: [VERSION | NONCE | CIPHERTEXT]. " +
                "Got size=${encryptedData.size}"
            )
        }

        return try {
            val version = encryptedData[0]
            val nonce = encryptedData.copyOfRange(1, 1 + NONCE_LENGTH_BYTES)
            val ciphertext = encryptedData.copyOfRange(1 + NONCE_LENGTH_BYTES, encryptedData.size)

            EncryptionComponents(version, nonce, ciphertext)
        } catch (e: Exception) {
            throw SecurityException(
                "Failed to extract encryption components: ${e.message}",
                e
            )
        }
    }

    /**
     * Create properly formatted encrypted data
     * Combines version, nonce, and ciphertext
     */
    fun createEncryptedData(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return try {
            val version = byteArrayOf(ENCRYPTION_VERSION)
            version + nonce + ciphertext
        } catch (e: Exception) {
            throw SecurityException(
                "Failed to create encrypted data: ${e.message}",
                e
            )
        }
    }

    /**
     * Verify encryption integrity (checks format and size)
     */
    fun verifyEncryptionFormat(encryptedData: ByteArray): Boolean {
        return isEncrypted(encryptedData) &&
               encryptedData.size >= (1 + NONCE_LENGTH_BYTES + 16)  // +16 for GCM tag
    }

    // ========== Data Classes ==========

    /**
     * Encryption component container
     */
    data class EncryptionComponents(
        val version: Byte,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptionComponents

            if (version != other.version) return false
            if (!nonce.contentEquals(other.nonce)) return false
            if (!ciphertext.contentEquals(other.ciphertext)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = version.hashCode()
            result = 31 * result + nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }
    }
}
