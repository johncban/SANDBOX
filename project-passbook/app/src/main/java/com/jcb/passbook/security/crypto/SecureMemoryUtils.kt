package com.jcb.passbook.security.crypto

import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * SecureMemoryUtils - Secure memory management utilities
 *
 * ✅ CLASS NAME FIXED: SecureMemoryUtils (not SecurityMemoryUtils)
 * This matches the imports in AuditJournalManager, DatabaseKeyManager, and MasterKeyManager
 *
 * Provides cryptographically secure operations for:
 * - Secure random number generation
 * - Safe memory wiping to prevent data leakage
 * - Constant-time comparisons to prevent timing attacks
 * - Secure data copying and handling
 *
 * ✅ FIXED: Complete implementation with all required methods
 * ✅ FIXED: Proper syntax with all closing braces
 * ✅ FIXED: Enhanced security features for key rotation
 */
@Singleton
class SecureMemoryUtils @Inject constructor() {
    private val secureRandom = SecureRandom()

    // ═══════════════════════════════════════════════════════════════════
    // Random Number Generation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generate cryptographically secure random bytes
     *
     * @param size Number of random bytes to generate
     * @return ByteArray of cryptographically secure random bytes
     */
    fun generateSecureRandom(size: Int): ByteArray {
        require(size > 0) { "Size must be positive" }
        val random = ByteArray(size)
        secureRandom.nextBytes(random)
        Timber.d("Generated $size bytes of secure random data")
        return random
    }

    /**
     * Generate a secure random integer within a range
     *
     * @param bound Upper bound (exclusive)
     * @return Random integer between 0 (inclusive) and bound (exclusive)
     */
    fun generateSecureRandomInt(bound: Int): Int {
        require(bound > 0) { "Bound must be positive" }
        return secureRandom.nextInt(bound)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ByteArray Memory Wiping
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ NEW: Simple wipe - overwrites with zeros
     * Alias for compatibility with key rotation code
     */
    fun wipeByteArray(data: ByteArray) {
        if (data.isNotEmpty()) {
            data.fill(0)
            Timber.v("Wiped ${data.size} bytes")
        }
    }

    /**
     * Securely wipe a ByteArray using multi-pass overwrite
     *
     * Security: Uses 2-pass wiping:
     * 1. Overwrite with secure random data
     * 2. Overwrite with zeros
     *
     * This makes data recovery extremely difficult
     */
    fun secureWipe(data: ByteArray) {
        if (data.isEmpty()) return
        try {
            // First pass: overwrite with random data
            secureRandom.nextBytes(data)
            // Second pass: overwrite with zeros
            data.fill(0)
            Timber.v("Securely wiped ${data.size} bytes (2-pass)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to securely wipe ByteArray")
            // Fallback: at least zero it out
            data.fill(0)
        }
    }

    /**
     * Securely wipe multiple ByteArrays at once
     *
     * @param arrays Variable number of ByteArrays to wipe
     */
    fun secureWipe(vararg arrays: ByteArray) {
        arrays.forEach { array ->
            secureWipe(array)
        }
    }

    /**
     * ✅ NEW: Wipe ByteArray and return null (for cleanup patterns)
     *
     * Usage:
     * var sensitiveData: ByteArray? = getSensitiveData()
     * sensitiveData = secureMemoryUtils.wipeAndNull(sensitiveData)
     */
    fun wipeAndNull(data: ByteArray?): ByteArray? {
        data?.let { secureWipe(it) }
        return null
    }

    // ═══════════════════════════════════════════════════════════════════
    // CharArray Memory Wiping (for passwords)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Securely wipe a CharArray (used for password fields)
     *
     * Security: Uses 2-pass wiping:
     * 1. Overwrite with random characters
     * 2. Overwrite with null characters
     */
    fun secureWipe(data: CharArray) {
        if (data.isEmpty()) return
        try {
            // First pass: overwrite with random characters
            val random = Random.Default
            for (i in data.indices) {
                data[i] = random.nextInt(65536).toChar()
            }
            // Second pass: overwrite with null characters
            data.fill('\u0000')
            Timber.v("Securely wiped CharArray of ${data.size} characters")
        } catch (e: Exception) {
            Timber.e(e, "Failed to securely wipe CharArray")
            // Fallback: at least zero it out
            data.fill('\u0000')
        }
    }

    /**
     * ✅ NEW: Simple CharArray wipe (for compatibility)
     */
    fun wipeCharArray(data: CharArray) {
        data.fill('\u0000')
    }

    /**
     * ✅ NEW: Wipe String by converting to CharArray and wiping
     * Note: Strings are immutable, so this creates a mutable copy first
     */
    fun secureWipeString(value: String): String {
        val chars = value.toCharArray()
        secureWipe(chars)
        return ""
    }

    // ═══════════════════════════════════════════════════════════════════
    // Secure Data Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create a secure copy of ByteArray data
     *
     * @param source Source ByteArray to copy
     * @return New ByteArray with copied data
     */
    fun secureCopy(source: ByteArray): ByteArray {
        return source.copyOf()
    }

    /**
     * ✅ NEW: Create a defensive copy and wipe the original
     *
     * @param source Original ByteArray (will be wiped)
     * @return New copy of the data
     */
    fun moveToNewArray(source: ByteArray): ByteArray {
        val copy = source.copyOf()
        secureWipe(source)
        return copy
    }

    // ═══════════════════════════════════════════════════════════════════
    // Constant-Time Comparisons (Timing Attack Prevention)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Compare two byte arrays in constant time to prevent timing attacks
     *
     * Security: Always compares all bytes regardless of early mismatch
     * This prevents attackers from using timing information to guess values
     *
     * @param a First ByteArray
     * @param b Second ByteArray
     * @return true if arrays are equal, false otherwise
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
     * ✅ NEW: Constant-time string comparison
     *
     * @param a First String
     * @param b Second String
     * @return true if strings are equal, false otherwise
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray()
        val bBytes = b.toByteArray()
        val result = constantTimeEquals(aBytes, bBytes)
        // Wipe temporary byte arrays
        secureWipe(aBytes)
        secureWipe(bBytes)
        return result
    }

    // ═══════════════════════════════════════════════════════════════════
    // Encoding/Decoding Helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ NEW: Convert ByteArray to hexadecimal string
     * Used for SQLCipher hex key format
     *
     * @param bytes ByteArray to convert
     * @return Hexadecimal string representation
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * ✅ NEW: Convert ByteArray to SQLCipher hex key format
     * Format: x'HEXSTRING'
     *
     * @param key ByteArray key
     * @return SQLCipher-compatible hex key string
     */
    fun toSQLCipherHexKey(key: ByteArray): String {
        return "x'${bytesToHex(key)}'"
    }

    /**
     * ✅ NEW: Convert hex string to ByteArray
     *
     * @param hex Hexadecimal string (without x' prefix)
     * @return ByteArray representation
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace("x'", "").replace("'", "").replace(" ", "")
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Memory Pressure Management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ NEW: Request garbage collection (best effort)
     * Call after wiping large amounts of sensitive data
     */
    fun requestGC() {
        try {
            System.gc()
            Timber.v("Requested garbage collection")
        } catch (e: Exception) {
            Timber.w(e, "Failed to request GC")
        }
    }

    /**
     * ✅ NEW: Execute a block with sensitive data and ensure cleanup
     *
     * Usage:
     * val result = secureMemoryUtils.withSensitiveData(sensitiveKey) { key ->
     *     performOperation(key)
     * }
     */
    inline fun <T> withSensitiveData(data: ByteArray, block: (ByteArray) -> T): T {
        return try {
            block(data)
        } finally {
            secureWipe(data)
        }
    }

    /**
     * ✅ NEW: Execute a block with sensitive char data and ensure cleanup
     */
    inline fun <T> withSensitiveData(data: CharArray, block: (CharArray) -> T): T {
        return try {
            block(data)
        } finally {
            secureWipe(data)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Validation Helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ NEW: Validate that a ByteArray is not empty and has expected size
     *
     * @param data ByteArray to validate
     * @param expectedSize Expected size in bytes (null to skip size check)
     * @param name Name for error messages
     * @throws IllegalArgumentException if validation fails
     */
    fun validateByteArray(data: ByteArray?, expectedSize: Int? = null, name: String = "data") {
        requireNotNull(data) { "$name cannot be null" }
        require(data.isNotEmpty()) { "$name cannot be empty" }
        if (expectedSize != null) {
            require(data.size == expectedSize) {
                "$name has invalid size: ${data.size} bytes (expected $expectedSize)"
            }
        }
    }

    /**
     * ✅ NEW: Check if ByteArray is all zeros (potentially wiped/corrupted)
     */
    fun isAllZeros(data: ByteArray): Boolean {
        return data.all { it == 0.toByte() }
    }

    /**
     * ✅ NEW: Calculate entropy of ByteArray (for randomness checking)
     * Returns value between 0.0 (no entropy) and 1.0 (maximum entropy)
     */
    fun calculateEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val frequencies = IntArray(256)
        data.forEach { byte ->
            frequencies[byte.toInt() and 0xFF]++
        }

        var entropy = 0.0
        val length = data.size.toDouble()
        frequencies.forEach { count ->
            if (count > 0) {
                val probability = count / length
                entropy -= probability * Math.log(probability) / Math.log(2.0)
            }
        }
        // Normalize to 0.0 - 1.0 range (max entropy for byte is 8 bits)
        return entropy / 8.0
    }

    // ═══════════════════════════════════════════════════════════════════
    // Debugging & Testing Support
    // ═══════════════════════════════════════════════════════════════════

    /**
     * ✅ NEW: Get safe string representation of ByteArray for logging
     * Shows only size and entropy, not actual data
     */
    fun safeToString(data: ByteArray?): String {
        if (data == null) return "null"
        if (data.isEmpty()) return "empty"
        val entropy = calculateEntropy(data)
        return "ByteArray[size=${data.size}, entropy=${String.format("%.2f", entropy)}]"
    }

    /**
     * ✅ NEW: Get masked hex string for debugging (shows first/last 4 bytes only)
     */
    fun maskedHex(data: ByteArray): String {
        if (data.isEmpty()) return "empty"
        if (data.size <= 8) return "***hidden***"
        val first4 = data.take(4).joinToString("") { "%02x".format(it) }
        val last4 = data.takeLast(4).joinToString("") { "%02x".format(it) }
        return "$first4...${data.size - 8} bytes...$last4"
    }
}