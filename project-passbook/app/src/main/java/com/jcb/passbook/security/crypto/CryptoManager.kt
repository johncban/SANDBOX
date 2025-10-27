package com.jcb.passbook.security.crypto

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

/**
 * CryptoManager handles all cryptographic operations for Passbook
 *
 * Security guarantees:
 * - AES-256-GCM for authenticated encryption
 * - Argon2id for password key derivation (via Bouncy Castle)
 * - PBKDF2-SHA512 as fallback key derivation
 * - SHA-256 for integrity verification
 * - Cryptographically secure random generation
 * - AndroidKeystore integration for secure key storage
 */
open class CryptoManager {

    companion object {
        private const val TAG = "CryptoManager"

        // AES-GCM Constants
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128

        // Key Derivation Constants
        private const val PBKDF2_ITERATION_COUNT = 600000
        private const val PBKDF2_KEY_SIZE_BITS = 256
        private const val ARGON2_SALT_SIZE_BYTES = 16

        // Hash Constants
        private const val SHA256_OUTPUT_SIZE_BYTES = 32

        init {
            // Add Bouncy Castle as a security provider
            Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        }
    }

    // ==================== AES-256-GCM Encryption ====================

    /**
     * Encrypt plaintext using AES-256-GCM
     *
     * @param plaintext Data to encrypt
     * @param key 256-bit encryption key
     * @param iv 96-bit IV (nonce)
     * @return Ciphertext with authentication tag
     * @throws IllegalArgumentException if key or IV size is invalid
     */
    fun encryptAES256GCM(
        plaintext: ByteArray,
        key: ByteArray,
        iv: ByteArray
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BITS / 8) {
            "Key must be 256 bits (32 bytes), got ${key.size}"
        }
        require(iv.size == GCM_IV_SIZE_BYTES) {
            "IV must be 96 bits (12 bytes), got ${iv.size}"
        }

        try {
            val secretKey = SecretKeySpec(key, 0, key.size, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            return cipher.doFinal(plaintext)
        } catch (e: Exception) {
            Log.e(TAG, "AES-256-GCM encryption failed: ${e.message}", e)
            throw CryptoException("AES-256-GCM encryption failed", e)
        }
    }

    /**
     * Decrypt ciphertext using AES-256-GCM
     *
     * @param ciphertext Data to decrypt (includes authentication tag)
     * @param key 256-bit decryption key
     * @param iv 96-bit IV (nonce)
     * @return Plaintext
     * @throws CryptoException if authentication fails or decryption fails
     * @throws IllegalArgumentException if key or IV size is invalid
     */
    fun decryptAES256GCM(
        ciphertext: ByteArray,
        key: ByteArray,
        iv: ByteArray
    ): ByteArray {
        require(key.size == AES_KEY_SIZE_BITS / 8) {
            "Key must be 256 bits (32 bytes), got ${key.size}"
        }
        require(iv.size == GCM_IV_SIZE_BYTES) {
            "IV must be 96 bits (12 bytes), got ${iv.size}"
        }
        require(ciphertext.isNotEmpty()) {
            "Ciphertext cannot be empty"
        }

        try {
            val secretKey = SecretKeySpec(key, 0, key.size, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e(TAG, "AES-256-GCM decryption/authentication failed: ${e.message}", e)
            throw CryptoException("AES-256-GCM authentication tag verification failed", e)
        }
    }

    // ==================== Simplified Encryption API ====================

    /**
     * Encrypt data with automatic IV generation
     * Returns IV + Ciphertext combined
     */
    open fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val iv = generateGCMIV()
        val ciphertext = encryptAES256GCM(plaintext, key, iv)
        return iv + ciphertext
    }

    /**
     * Decrypt data (expects IV + Ciphertext format)
     */
    open fun decrypt(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size > GCM_IV_SIZE_BYTES) {
            "Data must be at least ${GCM_IV_SIZE_BYTES + 1} bytes (IV + ciphertext)"
        }
        val iv = data.sliceArray(0 until GCM_IV_SIZE_BYTES)
        val ciphertext = data.sliceArray(GCM_IV_SIZE_BYTES until data.size)
        return decryptAES256GCM(ciphertext, key, iv)
    }

    /**
     * Encrypt string and return Base64 encoded result
     */
    fun encryptString(plaintext: String, key: ByteArray): String {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8), key)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypt Base64 encoded string
     */
    fun decryptString(encryptedBase64: String, key: ByteArray): String {
        val encryptedBytes = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
        val decrypted = decrypt(encryptedBytes, key)
        return String(decrypted, Charsets.UTF_8)
    }

    // ==================== ItemViewModel Compatibility Methods ====================

    /**
     * Encrypt password for ItemViewModel (uses AndroidKeystore master key)
     * This method is for backward compatibility with ItemViewModel
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun encrypt(plainTextPassword: String): ByteArray {
        try {
            val key = getOrDeriveEncryptionKey()
            val iv = generateGCMIV()
            val plaintext = plainTextPassword.toByteArray(Charsets.UTF_8)
            val ciphertext = encryptAES256GCM(plaintext, key, iv)
            return iv + ciphertext
        } catch (e: Exception) {
            Log.e(TAG, "Password encryption failed: ${e.message}", e)
            throw CryptoException("Password encryption failed", e)
        }
    }

    /**
     * Decrypt password for ItemViewModel (uses AndroidKeystore master key)
     * This method is for backward compatibility with ItemViewModel
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun decrypt(encryptedPasswordData: ByteArray): String {
        try {
            require(encryptedPasswordData.size > GCM_IV_SIZE_BYTES) {
                "Encrypted data too short (${encryptedPasswordData.size} bytes)"
            }
            val iv = encryptedPasswordData.sliceArray(0 until GCM_IV_SIZE_BYTES)
            val ciphertext = encryptedPasswordData.sliceArray(GCM_IV_SIZE_BYTES until encryptedPasswordData.size)
            val key = getOrDeriveEncryptionKey()
            val plaintext = decryptAES256GCM(ciphertext, key, iv)
            return plaintext.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Password decryption failed: ${e.message}", e)
            throw CryptoException("Password decryption failed", e)
        }
    }

    /**
     * Get or derive the master encryption key from AndroidKeystore
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getOrDeriveEncryptionKey(): ByteArray {
        val keyAlias = "passbook_master_key_v1"

        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (keyStore.containsAlias(keyAlias)) {
                Log.d(TAG, "Using existing encryption key from AndroidKeystore")
                return deriveWorkingKeyFromKeystore(keyAlias)
            }

            Log.d(TAG, "Generating new encryption key in AndroidKeystore")
            val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(false)
                .build()

            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()

            return deriveWorkingKeyFromKeystore(keyAlias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get/generate encryption key from AndroidKeystore", e)
            throw CryptoException("Failed to initialize encryption key", e)
        }
    }

    /**
     * Derive a usable 32-byte key from AndroidKeystore key
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun deriveWorkingKeyFromKeystore(keyAlias: String): ByteArray {
        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val entry = keyStore.getEntry(keyAlias, null) as java.security.KeyStore.SecretKeyEntry
            val key = entry.secretKey

            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val fixedIV = ByteArray(12) { 0 }
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, fixedIV))

            val salt = "passbook_key_salt_v1".toByteArray()
            val derived = cipher.doFinal(salt)

            return derived.sliceArray(0 until 32)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to derive working key", e)
            throw CryptoException("Failed to derive key", e)
        }
    }

    // ==================== Key Derivation ====================

    /**
     * Derive a cryptographic key using Argon2id with Bouncy Castle
     */
    fun deriveKeyArgon2id(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int = 3,
        memory: Int = 65536,
        parallelism: Int = 1
    ): ByteArray {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(salt.size >= ARGON2_SALT_SIZE_BYTES) { "Salt must be at least 16 bytes" }
        require(iterations >= 2) { "Iterations must be at least 2" }
        require(memory >= 8) { "Memory must be at least 8 KiB, recommended 65536 KiB (64 MiB)" }
        require(parallelism >= 1) { "Parallelism must be at least 1" }

        val passwordChars = String(password, Charsets.UTF_8).toCharArray()
        try {
            val paramsBuilder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memory)
                .withParallelism(parallelism)
                .withSalt(salt)

            val generator = Argon2BytesGenerator()
            generator.init(paramsBuilder.build())

            val derivedKey = ByteArray(AES_KEY_SIZE_BITS / 8)
            generator.generateBytes(passwordChars, derivedKey, 0, derivedKey.size)

            return derivedKey
        } catch (e: Exception) {
            Log.e(TAG, "Argon2id (Bouncy Castle) derivation failed: ${e.message}", e)
            Log.w(TAG, "Falling back to PBKDF2-SHA512")
            return derivePBKDF2SHA512(password, salt)
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Derive cryptographic key using PBKDF2-SHA512
     */
    fun derivePBKDF2SHA512(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATION_COUNT
    ): ByteArray {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(salt.size >= ARGON2_SALT_SIZE_BYTES) {
            "Salt must be at least 16 bytes, got ${salt.size}"
        }
        require(iterations >= 100000) { "Iterations must be at least 100000" }

        return try {
            val passwordString = String(password, Charsets.UTF_8)
            val keySpec = PBEKeySpec(
                passwordString.toCharArray(),
                salt,
                iterations,
                PBKDF2_KEY_SIZE_BITS
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
            val key = factory.generateSecret(keySpec)
            key.encoded
        } catch (e: Exception) {
            Log.e(TAG, "PBKDF2-SHA512 derivation failed: ${e.message}", e)
            throw CryptoException("PBKDF2-SHA512 derivation failed", e)
        }
    }

    // ==================== Hashing ====================

    fun hashSHA256(data: ByteArray): ByteArray {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(data)
        } catch (e: Exception) {
            Log.e(TAG, "SHA-256 hashing failed: ${e.message}", e)
            throw CryptoException("SHA-256 hashing failed", e)
        }
    }

    fun hashSHA256WithSalt(data: ByteArray, salt: ByteArray): ByteArray {
        return try {
            val input = salt + data
            hashSHA256(input)
        } catch (e: Exception) {
            Log.e(TAG, "SHA-256 with salt hashing failed: ${e.message}", e)
            throw CryptoException("SHA-256 with salt hashing failed", e)
        }
    }

    fun hmacSHA256(data: ByteArray, key: ByteArray): ByteArray {
        return try {
            val algorithm = "HmacSHA256"
            val mac = javax.crypto.Mac.getInstance(algorithm)
            val secretKey = SecretKeySpec(key, 0, key.size, algorithm)
            mac.init(secretKey)
            mac.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "HMAC-SHA256 failed: ${e.message}", e)
            throw CryptoException("HMAC-SHA256 failed", e)
        }
    }

    // ==================== Random Generation ====================

    fun generateSecureRandom(size: Int): ByteArray {
        require(size > 0) { "Size must be greater than 0" }
        return ByteArray(size).apply {
            SecureRandom().nextBytes(this)
        }
    }

    fun generateGCMIV(): ByteArray {
        return generateSecureRandom(GCM_IV_SIZE_BYTES)
    }

    fun generateDerivationSalt(): ByteArray {
        return generateSecureRandom(ARGON2_SALT_SIZE_BYTES)
    }

    fun generateRandomKey(): ByteArray {
        return generateSecureRandom(AES_KEY_SIZE_BITS / 8)
    }

    // ==================== Utility Functions ====================

    fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    fun String.hexStringToByteArray(): ByteArray {
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun secureWipeByteArray(data: ByteArray) {
        data.fill(0)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].xor(b[i]).toInt())
        }

        return result == 0
    }
}

/**
 * Custom exception for cryptographic operations
 */
class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)