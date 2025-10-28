package com.jcb.passbook.security.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ephemeral session passphrase derivation from biometric-protected master seed
 * Replaces persistent passphrase model with session-derived, ephemeral approach
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class SessionPassphraseManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SessionPassphraseManager"
        private const val MASTER_SEED_KEY_ALIAS = "passbook_master_seed_key"
        private const val PREF_NAME = "master_seed_prefs"
        private const val ENCRYPTED_SEED_KEY = "encrypted_master_seed"
        private const val SEED_SIZE_BYTES = 32
        private const val SALT_SIZE_BYTES = 32

        // Argon2 parameters - balanced for mobile security vs performance
        private const val ARGON2_MEMORY_KB = 65536 // 64MB
        private const val ARGON2_ITERATIONS = 3
        private const val ARGON2_PARALLELISM = 1
        private const val ARGON2_HASH_LENGTH = 32
    }

    private val argon2 = Argon2Kt()

    /**
     * Result of seed derivation operation
     */
    sealed class DerivationResult {
        data class Success(val sessionKey: ByteArray, val salt: ByteArray) : DerivationResult()
        data class Error(val message: String) : DerivationResult()
    }

    /**
     * Initializes master seed if not present, encrypted with biometric-required keystore key
     */
    fun initializeMasterSeed(): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(ENCRYPTED_SEED_KEY)) {
                Timber.d("Generating new master seed")
                val masterSeed = generateRandomSeed()
                val encryptedSeed = encryptMasterSeed(masterSeed)
                prefs.edit()
                    .putString(ENCRYPTED_SEED_KEY, encryptedSeed)
                    .apply()

                // Clear plaintext seed from memory
                masterSeed.fill(0)
                Timber.d("Master seed initialized and encrypted")
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize master seed")
            false
        }
    }

    /**
     * Derives ephemeral session passphrase from master seed using biometric authentication
     */
    fun deriveSessionPassphrase(biometricCipher: Cipher?): DerivationResult {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val encryptedSeed = prefs.getString(ENCRYPTED_SEED_KEY, null)
                ?: return DerivationResult.Error("No master seed found. Please reinitialize.")

            // Decrypt master seed using biometric-authenticated cipher
            val masterSeed = if (biometricCipher != null) {
                decryptMasterSeedWithBiometric(encryptedSeed, biometricCipher)
            } else {
                decryptMasterSeed(encryptedSeed)
            } ?: return DerivationResult.Error("Failed to decrypt master seed")

            // Generate fresh salt for this session
            val sessionSalt = generateRandomSalt()

            // Derive session key using Argon2
            val sessionKey = deriveKeyWithArgon2(masterSeed, sessionSalt)

            // Clear master seed from memory immediately
            masterSeed.fill(0)

            if (sessionKey != null) {
                Timber.d("Session passphrase derived successfully")
                DerivationResult.Success(sessionKey, sessionSalt)
            } else {
                DerivationResult.Error("Argon2 key derivation failed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Session passphrase derivation failed")
            DerivationResult.Error("Derivation failed: ${e.message}")
        }
    }

    /**
     * Derives session passphrase from password (fallback when biometric unavailable)
     */
    fun deriveSessionFromPassword(password: String): DerivationResult {
        return try {
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
            val sessionSalt = generateRandomSalt()

            // Derive session key directly from password + salt
            val sessionKey = deriveKeyWithArgon2(passwordBytes, sessionSalt)

            // Clear password bytes
            passwordBytes.fill(0)

            if (sessionKey != null) {
                Timber.d("Session passphrase derived from password")
                DerivationResult.Success(sessionKey, sessionSalt)
            } else {
                DerivationResult.Error("Password-based derivation failed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Password-based derivation failed")
            DerivationResult.Error("Password derivation failed: ${e.message}")
        }
    }

    /**
     * Rotates the master seed (for security events or periodic rotation)
     */
    fun rotateMasterSeed(): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val newMasterSeed = generateRandomSeed()
            val encryptedSeed = encryptMasterSeed(newMasterSeed)

            prefs.edit()
                .putString(ENCRYPTED_SEED_KEY, encryptedSeed)
                .apply()

            // Clear new seed from memory
            newMasterSeed.fill(0)
            Timber.w("Master seed rotated")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate master seed")
            false
        }
    }

    /**
     * Checks if master seed is initialized
     */
    fun isMasterSeedInitialized(): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.contains(ENCRYPTED_SEED_KEY)
    }

    private fun generateRandomSeed(): ByteArray {
        val seed = ByteArray(SEED_SIZE_BYTES)
        SecureRandom().nextBytes(seed)
        return seed
    }

    private fun generateRandomSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun getBiometricRequiredKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getKey(MASTER_SEED_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MASTER_SEED_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(0) // Require auth for each use
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encryptMasterSeed(seed: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = getBiometricRequiredKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(seed)

        // Combine IV + encrypted data
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptMasterSeed(encryptedSeed: String): ByteArray? {
        return try {
            val data = Base64.decode(encryptedSeed, Base64.NO_WRAP)
            if (data.size <= 12) return null

            val iv = data.copyOfRange(0, 12)
            val encrypted = data.copyOfRange(12, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getBiometricRequiredKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt master seed")
            null
        }
    }

    private fun decryptMasterSeedWithBiometric(encryptedSeed: String, cipher: Cipher): ByteArray? {
        return try {
            val data = Base64.decode(encryptedSeed, Base64.NO_WRAP)
            if (data.size <= 12) return null

            val encrypted = data.copyOfRange(12, data.size)
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt master seed with biometric cipher")
            null
        }
    }

    private fun deriveKeyWithArgon2(input: ByteArray, salt: ByteArray): ByteArray? {
        return try {
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = input,
                salt = salt,
                tCostInIterations = ARGON2_ITERATIONS,
                mCostInKibibyte = ARGON2_MEMORY_KB,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = ARGON2_HASH_LENGTH
            )
            result.rawHashAsByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Argon2 derivation failed")
            null
        }
    }
}
