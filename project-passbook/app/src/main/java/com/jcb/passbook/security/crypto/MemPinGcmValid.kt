package com.jcb.passbook.security.crypto

import timber.log.Timber
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * ✅ BUG-002: Secure Memory Management Utilities
 *
 * Provides secure memory handling for sensitive data:
 * 1. Secure random generation with proper seeding
 * 2. Memory zeroing with verification
 * 3. Protected storage with locks
 * 4. Explicit cleanup on shutdown
 *
 * Addresses memory leak vulnerability where encryption keys
 * remain in memory after app termination.
 */
object MemoryPinning {

    private const val TAG = "MemoryPinning"

    private val secureRandom: SecureRandom by lazy {
        try {
            // Use strongest available PRNG
            SecureRandom.getInstanceStrong()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "StrongSecureRandom unavailable, falling back to default")
            SecureRandom()
        }
    }

    /**
     * ✅ BUG-002: Generate cryptographically secure random bytes.
     *
     * @param size Number of bytes to generate.
     */
    fun generateSecureRandom(size: Int): ByteArray {
        require(size > 0) { "Size must be > 0" }

        Timber.tag(TAG).d("Generating $size secure random bytes")
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        Timber.tag(TAG).d("✓ Generated $size secure random bytes")
        return bytes
    }

    /**
     * ✅ BUG-002: Securely zero out byte-array memory.
     *
     * Overwrites memory with zeros and verifies completion.
     * Uses a simple loop to avoid JIT optimization issues.
     */
    fun zeroMemory(data: ByteArray?) {
        if (data == null) return

        Timber.tag(TAG).d("Zeroing ${data.size} bytes from memory")
        try {
            var i = 0
            while (i < data.size) {
                data[i] = 0x00
                i++
            }

            // Verify zeroing
            var isZeroed = true
            var j = 0
            while (j < data.size && isZeroed) {
                if (data[j] != 0x00.toByte()) {
                    isZeroed = false
                }
                j++
            }

            if (isZeroed) {
                Timber.tag(TAG).d("✓ Memory zeroing verified")
            } else {
                Timber.tag(TAG).e("❌ Memory zeroing verification FAILED")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to zero memory")
        }
    }

    /**
     * ✅ BUG-002: Securely zero CharArray (for passwords).
     */
    fun zeroMemory(data: CharArray?) {
        if (data == null) return

        Timber.tag(TAG).d("Zeroing ${data.size} chars from memory")
        try {
            var i = 0
            while (i < data.size) {
                data[i] = '\u0000'
                i++
            }

            // Verify
            var verified = true
            var j = 0
            while (j < data.size && verified) {
                if (data[j] != '\u0000') {
                    verified = false
                }
                j++
            }

            if (verified) {
                Timber.tag(TAG).d("✓ CharArray zeroing verified")
            } else {
                Timber.tag(TAG).e("❌ CharArray zeroing verification FAILED")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to zero CharArray")
        }
    }
}

/**
 * ✅ BUG-002: Protected byte array wrapper with automatic cleanup.
 *
 * - Thread-safe read/write via ReentrantReadWriteLock
 * - Explicit cleanup via [cleanup]
 * - Best-effort cleanup on GC via [finalize]
 *
 * NOTE: Marked `internal` and methods are non-inline to avoid
 * "public API inline function cannot access non-public-API property" warnings.
 */
internal class ProtectedByteArray(initialData: ByteArray) {

    private val TAG = "ProtectedByteArray"

    private val lock = ReentrantReadWriteLock()

    // backing storage; must not be exposed directly
    private var data: ByteArray = initialData.copyOf()

    private var isCleanedUp: Boolean = false

    init {
        Timber.tag(TAG).d("Created ProtectedByteArray (${data.size} bytes)")
    }

    /**
     * Use protected data safely. The lambda is executed while holding a read lock.
     * Throws IllegalStateException if data has been cleaned up.
     */
    fun <T> withData(block: (ByteArray) -> T): T {
        return lock.read {
            if (isCleanedUp) {
                throw IllegalStateException("ProtectedByteArray has been cleaned up")
            }
            block(data)
        }
    }

    /**
     * Get size without exposing data.
     */
    fun size(): Int = lock.read {
        if (isCleanedUp) 0 else data.size
    }

    /**
     * Explicitly cleanup data from memory.
     */
    fun cleanup() {
        lock.write {
            if (!isCleanedUp) {
                Timber.tag(TAG).d("Cleaning up ProtectedByteArray (${data.size} bytes)")
                MemoryPinning.zeroMemory(data)
                isCleanedUp = true
            }
        }
    }

    /**
     * Best-effort cleanup on garbage collection.
     */
    @Suppress("deprecation")
    protected fun finalize() {
        try {
            cleanup()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception during ProtectedByteArray.finalize")
        }
    }
}

/**
 * ✅ BUG-018: GCM parameter validation.
 *
 * Ensures consistent GCM tag length and validates parameters.
 */
object GcmValidator {

    private const val TAG = "GcmValidator"

    // Standard GCM tag length: 128 bits = 16 bytes
    const val STANDARD_GCM_TAG_LENGTH_BITS: Int = 128
    const val STANDARD_GCM_TAG_LENGTH_BYTES: Int = STANDARD_GCM_TAG_LENGTH_BITS / 8

    /**
     * Validate GCM tag length in bits.
     */
    fun validateTagLength(tagLengthBits: Int) {
        if (tagLengthBits != STANDARD_GCM_TAG_LENGTH_BITS) {
            throw IllegalArgumentException(
                "Invalid GCM tag length: $tagLengthBits bits (expected $STANDARD_GCM_TAG_LENGTH_BITS bits)"
            )
        }
        Timber.tag(TAG).d("✓ GCM tag length validated: $tagLengthBits bits")
    }

    /**
     * Validate IV size in bytes (GCM recommends 12-byte IV).
     */
    fun validateIvSize(ivSize: Int) {
        val expectedSize = 12 // 96 bits
        if (ivSize != expectedSize) {
            throw IllegalArgumentException(
                "Invalid GCM IV size: $ivSize bytes (expected $expectedSize bytes)"
            )
        }
        Timber.tag(TAG).d("✓ GCM IV size validated: $ivSize bytes")
    }

    /**
     * Validate ciphertext minimum size (must at least contain GCM tag).
     */
    fun validateCiphertextSize(ciphertextSize: Int) {
        if (ciphertextSize < STANDARD_GCM_TAG_LENGTH_BYTES) {
            throw IllegalArgumentException(
                "Ciphertext too short: $ciphertextSize bytes " +
                        "(minimum $STANDARD_GCM_TAG_LENGTH_BYTES bytes for GCM tag)"
            )
        }
        Timber.tag(TAG).d("✓ Ciphertext size validated: $ciphertextSize bytes")
    }
}
