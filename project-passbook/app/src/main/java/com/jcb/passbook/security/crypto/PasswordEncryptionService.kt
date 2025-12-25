package com.jcb.passbook.security.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * ✅ UPDATED: PasswordEncryptionService with GCM authentication
 *
 * Changes:
 * 1. Integrates CryptoManager for metadata-based detection
 * 2. Uses GCM mode for authenticated encryption
 * 3. Adds encryption metadata header
 * 4. Includes PasswordBasedKeyDerivation utility
 */
class PasswordEncryptionService @Inject constructor(
    private val cryptoManager: CryptoManager
) {
    companion object {
        private const val KEY_ALIAS = "passbook_amk_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_NONCE_LENGTH = 12
    }

    /**
     * ✅ CRITICAL: Encrypt password with GCM authentication
     *
     * Returns: [VERSION | NONCE | CIPHERTEXT | AUTH_TAG]
     */
    fun encryptPassword(plainPassword: String, amk: ByteArray): ByteArray {
        return try {
            Timber.d("🔐 Encrypting password...")

            // Generate random nonce
            val nonce = ByteArray(GCM_NONCE_LENGTH)
            SecureRandom().nextBytes(nonce)

            // Create cipher with GCM
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)

            val secretKey = createSecretKey(amk)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

            // Encrypt password
            val ciphertext = cipher.doFinal(plainPassword.toByteArray(Charsets.UTF_8))

            // Create encrypted data with metadata
            val encryptedData = cryptoManager.createEncryptedData(nonce, ciphertext)

            Timber.d("✅ Password encrypted successfully")
            encryptedData

        } catch (e: Exception) {
            Timber.e(e, "❌ Encryption failed")
            throw SecurityException("Password encryption failed: ${e.message}", e)
        }
    }

    /**
     * ✅ CRITICAL: Decrypt password with validation
     */
    fun decryptPassword(encryptedData: ByteArray, amk: ByteArray): String {
        return try {
            Timber.d("🔐 Decrypting password...")

            // Validate encryption format
            if (!cryptoManager.isEncrypted(encryptedData)) {
                throw IllegalArgumentException("Data is not properly encrypted")
            }

            // Extract components
            val components = cryptoManager.extractEncryptionComponents(encryptedData)

            // Create cipher with GCM
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, components.nonce)

            val secretKey = createSecretKey(amk)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            // Decrypt
            val plainPassword = String(
                cipher.doFinal(components.ciphertext),
                Charsets.UTF_8
            )

            Timber.d("✅ Password decrypted successfully")
            plainPassword

        } catch (e: Exception) {
            Timber.e(e, "❌ Decryption failed")
            throw SecurityException("Password decryption failed: ${e.message}", e)
        }
    }

    /**
     * Create AES secret key from AMK
     */
    @VisibleForTesting
    fun createSecretKey(amk: ByteArray): SecretKey {
        return object : SecretKey {
            override fun getAlgorithm(): String = "AES"
            override fun getFormat(): String = "RAW"
            override fun getEncoded(): ByteArray = amk
        }
    }

    /**
     * Initialize keystore (called once at app startup)
     */
    fun initializeKeystore() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            // Check if key already exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                Timber.d("✓ Keystore already initialized")
                return
            }

            // Generate new key
            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )

            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()

            keyGen.init(keySpec)
            keyGen.generateKey()

            Timber.i("✅ Keystore initialized")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize keystore")
        }
    }
}

/**
 * ✅ NEW: PasswordBasedKeyDerivation utility
 * PBKDF2 with 600,000 iterations (NIST 2023 recommendation)
 */
class PasswordBasedKeyDerivation {
    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 600_000  // NIST 2023
        private const val KEY_SIZE = 256  // AES-256
        private const val SALT_SIZE = 32  // 256 bits
    }

    fun deriveKey(userPassword: String, salt: ByteArray?): Pair<ByteArray, ByteArray> {
        return try {
            // Use provided salt or generate new one
            val derivedSalt = salt ?: ByteArray(SALT_SIZE).apply {
                SecureRandom().nextBytes(this)
            }

            // Derive key using PBKDF2
            val factory = javax.crypto.SecretKeyFactory.getInstance(ALGORITHM)
            val spec = javax.crypto.spec.PBEKeySpec(
                userPassword.toCharArray(),
                derivedSalt,
                ITERATIONS,
                KEY_SIZE
            )
            val key = factory.generateSecret(spec).encoded

            Pair(key, derivedSalt)
        } catch (e: Exception) {
            throw SecurityException("Key derivation failed: ${e.message}", e)
        }
    }

    fun verifyPassword(userPassword: String, salt: ByteArray, expectedKey: ByteArray): Boolean {
        return try {
            val (derivedKey, _) = deriveKey(userPassword, salt)
            derivedKey.contentEquals(expectedKey)
        } catch (e: Exception) {
            false
        }
    }
}
