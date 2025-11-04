package com.jcb.passbook.security.crypto

import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom
import kotlin.random.Random

/**
 * SecureMemoryUtils provides utilities for secure memory management,
 * wiping sensitive data from memory to minimize exposure time.
 */
@Singleton
class SecureMemoryUtils @Inject constructor() {

    /**
     * Securely wipe a ByteArray by overwriting with random data
     */
    fun secureWipe(data: ByteArray) {
        if (data.isNotEmpty()) {
            SecureRandom().nextBytes(data)
            // Second pass with zeros
            data.fill(0)
        }
    }

    /**
     * Securely wipe multiple ByteArrays
     */
    fun secureWipe(vararg arrays: ByteArray) {
        arrays.forEach { secureWipe(it) }
    }

    /**
     * Create a secure copy of ByteArray data
     */
    fun secureCopy(source: ByteArray): ByteArray {
        return source.copyOf()
    }

    /**
     * Compare two byte arrays in constant time to prevent timing attacks
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Generate cryptographically secure random bytes
     */
    fun generateSecureRandom(size: Int): ByteArray {
        val random = ByteArray(size)
        SecureRandom().nextBytes(random)
        return random
    }

    /**
     * Secure CharArray wiper for password fields
     */
    fun secureWipe(data: CharArray) {
        if (data.isNotEmpty()) {
            val random = Random.Default
            for (i in data.indices) {
                data[i] = random.nextInt(65536).toChar()
            }
            data.fill('\u0000')
        }
    }
}