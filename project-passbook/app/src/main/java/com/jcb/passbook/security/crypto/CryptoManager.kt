package com.jcb.passbook.security.crypto

import android.annotation.SuppressLint
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
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CryptoManager"
private const val ALIAS = "password_data_key_alias"
private const val IV_SIZE = 12
private const val GCM_TAG_LENGTH = 128

/**
 * ✅ FIXED: Robust error handling for keystore operations
 * - Catches all keystore/cipher failures with structured logging
 * - Validates IV and ciphertext lengths before operations
 * - Provides clear error messages for debugging
 */
@Singleton
open class CryptoManager @Inject constructor() {

    sealed class CryptoResult<out T> {
        data class Success<T>(val data: T) : CryptoResult<T>()
        data class Failure(val error: CryptoError) : CryptoResult<Nothing>()
    }

    sealed class CryptoError(val message: String, val cause: Throwable? = null) {
        class KeystoreError(message: String, cause: Throwable) : CryptoError(message, cause)
        class EncryptionError(message: String, cause: Throwable) : CryptoError(message, cause)
        class DecryptionError(message: String, cause: Throwable) : CryptoError(message, cause)
        class InvalidDataError(message: String) : CryptoError(message)
    }

    private val keyStore: KeyStore = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "CRITICAL: Failed to load AndroidKeyStore")
        throw IllegalStateException("Keystore initialization failed", e)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getKey(): CryptoResult<SecretKey> {
        return try {
            val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            val key = entry?.secretKey ?: return createKey()

            Timber.tag(TAG).d("✓ Retrieved existing key from keystore")
            CryptoResult.Success(key)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to retrieve key from keystore")
            CryptoResult.Failure(CryptoError.KeystoreError("Key retrieval failed", e))
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createKey(): CryptoResult<SecretKey> {
        return try {
            Timber.tag(TAG).d("Creating new encryption key in keystore")

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            val key = keyGenerator.generateKey()

            Timber.tag(TAG).i("✓ New encryption key created successfully")
            CryptoResult.Success(key)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "CRITICAL: Failed to create encryption key")
            CryptoResult.Failure(CryptoError.KeystoreError("Key creation failed", e))
        }
    }

    /**
     * ✅ Encrypts plaintext with comprehensive error handling
     * @return CryptoResult containing encrypted data or error
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun encryptSafe(plainText: String): CryptoResult<ByteArray> {
        if (plainText.isEmpty()) {
            return CryptoResult.Failure(CryptoError.InvalidDataError("Cannot encrypt empty string"))
        }

        return when (val keyResult = getKey()) {
            is CryptoResult.Failure -> keyResult
            is CryptoResult.Success -> {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, keyResult.data)

                    val iv = cipher.iv
                    if (iv.size != IV_SIZE) {
                        Timber.tag(TAG).e("Invalid IV size: ${iv.size}, expected $IV_SIZE")
                        return CryptoResult.Failure(
                            CryptoError.EncryptionError("Invalid IV size generated", IllegalStateException())
                        )
                    }

                    val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                    val result = iv + encrypted

                    Timber.tag(TAG).d("✓ Encryption successful: IV=$IV_SIZE bytes, ciphertext=${encrypted.size} bytes")
                    CryptoResult.Success(result)

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Encryption operation failed")
                    CryptoResult.Failure(CryptoError.EncryptionError("Encryption failed", e))
                }
            }
        }
    }

    /**
     * ✅ LEGACY: Throws exceptions (for backward compatibility)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(Exception::class)
    open fun encrypt(plainText: String): ByteArray {
        return when (val result = encryptSafe(plainText)) {
            is CryptoResult.Success -> result.data
            is CryptoResult.Failure -> {
                throw Exception(result.error.message, result.error.cause)
            }
        }
    }

    /**
     * ✅ Decrypts data with comprehensive validation
     * @return CryptoResult containing decrypted string or error
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun decryptSafe(data: ByteArray): CryptoResult<String> {
        // ✅ Validate input length
        if (data.size <= IV_SIZE) {
            val error = "Data too short: ${data.size} bytes (need > $IV_SIZE for IV + ciphertext)"
            Timber.tag(TAG).e(error)
            return CryptoResult.Failure(CryptoError.InvalidDataError(error))
        }

        val iv = data.copyOfRange(0, IV_SIZE)
        val ciphertext = data.copyOfRange(IV_SIZE, data.size)

        // ✅ Validate minimum ciphertext size (GCM tag = 16 bytes minimum)
        if (ciphertext.size < 16) {
            val error = "Ciphertext too short: ${ciphertext.size} bytes (need ≥16 for GCM tag)"
            Timber.tag(TAG).e(error)
            return CryptoResult.Failure(CryptoError.InvalidDataError(error))
        }

        return when (val keyResult = getKey()) {
            is CryptoResult.Failure -> keyResult
            is CryptoResult.Success -> {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, keyResult.data, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                    val decrypted = cipher.doFinal(ciphertext)
                    val result = String(decrypted, Charsets.UTF_8)

                    Timber.tag(TAG).d("✓ Decryption successful: plaintext=${result.length} chars")
                    CryptoResult.Success(result)

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Decryption operation failed")
                    CryptoResult.Failure(CryptoError.DecryptionError("Decryption failed", e))
                }
            }
        }
    }

    /**
     * ✅ LEGACY: Throws exceptions (for backward compatibility)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(Exception::class)
    open fun decrypt(data: ByteArray): String {
        return when (val result = decryptSafe(data)) {
            is CryptoResult.Success -> result.data
            is CryptoResult.Failure -> {
                throw Exception(result.error.message, result.error.cause)
            }
        }
    }

    /**
     * ✅ NEW: Safely delete key and handle migration scenarios
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun deleteKey(): Boolean {
        return try {
            if (keyStore.containsAlias(ALIAS)) {
                keyStore.deleteEntry(ALIAS)
                Timber.tag(TAG).w("Encryption key deleted from keystore")
                true
            } else {
                Timber.tag(TAG).d("No key to delete")
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete encryption key")
            false
        }
    }

    /**
     * ✅ NEW: Check if key exists in keystore
     */
    fun hasKey(): Boolean {
        return try {
            keyStore.containsAlias(ALIAS)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to check key existence")
            false
        }
    }
}
