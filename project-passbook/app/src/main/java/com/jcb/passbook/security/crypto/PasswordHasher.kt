package com.jcb.passbook.security.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing utility using PBKDF2 with SHA-256
 */
object PasswordHasher {

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32

    /**
     * Result container for hash and salt
     */
    data class HashResult(
        val hash: ByteArray,
        val salt: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HashResult
            if (!hash.contentEquals(other.hash)) return false
            if (!salt.contentEquals(other.salt)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = hash.contentHashCode()
            result = 31 * result + salt.contentHashCode()
            return result
        }
    }

    /**
     * Hash a plaintext password
     * @param plainPassword Plaintext password to hash
     * @return HashResult containing hash and salt separately
     */
    fun hashPassword(plainPassword: String): HashResult {
        // Generate random salt
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        // Hash password with salt
        val hash = pbkdf2(plainPassword, salt)

        return HashResult(hash = hash, salt = salt)
    }

    /**
     * Verify a plaintext password against stored hash and salt
     * @param plainPassword Plaintext password to verify
     * @param passwordHash Stored password hash
     * @param salt Stored salt
     * @return true if password matches
     */
    fun verifyPassword(plainPassword: String, passwordHash: ByteArray, salt: ByteArray): Boolean {
        // Hash input password with same salt
        val inputHash = pbkdf2(plainPassword, salt)

        // Constant-time comparison
        return MessageDigest.isEqual(inputHash, passwordHash)
    }

    /**
     * PBKDF2 key derivation
     */
    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
