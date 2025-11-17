package com.jcb.passbook.security.audit

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditChainManager manages tamper-evident chaining of audit entries.
 * Each entry is linked to the previous one via cryptographic hashes (SHA-256/HMAC-SHA256),
 * making unauthorized deletion or modification detectable.
 *
 * Security Features:
 * - SHA-256 or SHA-512 cryptographic hash chaining
 * - Optional HMAC-SHA256 for enhanced security
 * - Optional secret salt for production environments
 * - Checksum validation for each entry
 * - Chain integrity verification
 * - Chain repair/recomputation
 *
 * Based on SECURITY NOTES requirements:
 * 1. SHA-256 vs SHA-512: Configurable via HASH_ALGORITHM
 * 2. Salt/Nonce: Secret salt can be configured via CHAIN_SALT
 * 3. HMAC Alternative: HMAC-SHA256 available via calculateHMAC()
 */
@Singleton
class AuditChainManager @Inject constructor(
    private val auditDao: AuditDao
) {

    companion object {
        /**
         * Genesis hash - the starting point of the audit chain.
         * Represents the hash before the first entry.
         */
        private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

        /**
         * Hash algorithm used for chain integrity.
         * Options: "SHA-256" (default), "SHA-512" (for extra security)
         */
        private const val HASH_ALGORITHM = "SHA-256"  // Change to "SHA-512" if needed

        /**
         * Secret salt for production systems handling sensitive data.
         * Store this securely (e.g., in Android Keystore or encrypted SharedPreferences)!
         * Leave empty for basic hashing, set a secret value for enhanced security.
         */
        private const val CHAIN_SALT = ""  // TODO: Set your-secret-salt-here for production

        /**
         * Use HMAC-SHA256 instead of simple SHA-256.
         * Set to true for stronger tamper-evident security.
         */
        private const val USE_HMAC = false  // Set to true for HMAC-SHA256

        /**
         * Secret key for HMAC (must be securely stored!)
         * Only used if USE_HMAC = true
         */
        private val HMAC_SECRET_KEY = "your-hmac-secret-key-here".toByteArray(Charsets.UTF_8)
    }

    /**
     * Verifies the integrity of the entire audit chain.
     * Checks:
     * 1. Chain linkage (chainPrevHash matches previous entry's chainHash)
     * 2. Hash correctness (chainHash computation is valid)
     * 3. Checksum validity (entry data hasn't been tampered)
     *
     * @return ChainVerificationResult with status and any detected errors
     */
    suspend fun verifyChain(): ChainVerificationResult = withContext(Dispatchers.IO) {
        try {
            val entries = auditDao.getAllAuditEntries().first()

            if (entries.isEmpty()) {
                return@withContext ChainVerificationResult(
                    isValid = true,
                    totalEntries = 0,
                    errors = emptyList()
                )
            }

            var prevHash: String? = GENESIS_HASH
            val errors = mutableListOf<ChainError>()

            entries.sortedBy { it.timestamp }.forEachIndexed { index, entry ->
                // Check 1: Chain linkage
                if (entry.chainPrevHash != prevHash) {
                    errors.add(
                        ChainError(
                            entryId = entry.id,
                            timestamp = entry.timestamp,
                            errorType = ChainErrorType.CHAIN_BREAK,
                            message = "Expected prev: $prevHash, got: ${entry.chainPrevHash}"
                        )
                    )
                }

                // Check 2: Hash correctness
                val expectedHash = calculateHash(entry.id, entry.timestamp, prevHash)
                if (entry.chainHash != expectedHash) {
                    errors.add(
                        ChainError(
                            entryId = entry.id,
                            timestamp = entry.timestamp,
                            errorType = ChainErrorType.INVALID_HASH,
                            message = "Expected: $expectedHash, got: ${entry.chainHash}"
                        )
                    )
                }

                // Check 3: Checksum validation
                val calculatedChecksum = entry.generateChecksum()
                if (entry.checksum != calculatedChecksum) {
                    errors.add(
                        ChainError(
                            entryId = entry.id,
                            timestamp = entry.timestamp,
                            errorType = ChainErrorType.CHECKSUM_MISMATCH,
                            message = "Expected: $calculatedChecksum, got: ${entry.checksum}"
                        )
                    )
                }

                prevHash = entry.chainHash
            }

            ChainVerificationResult(
                isValid = errors.isEmpty(),
                totalEntries = entries.size,
                errors = errors
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify audit chain")
            ChainVerificationResult(
                isValid = false,
                totalEntries = 0,
                errors = listOf(
                    ChainError(
                        entryId = -1,
                        timestamp = System.currentTimeMillis(),
                        errorType = ChainErrorType.VERIFICATION_FAILED,
                        message = "Exception during verification: ${e.message}"
                    )
                )
            )
        }
    }

    /**
     * Adds a new entry to the audit chain.
     * Automatically sets chainPrevHash, chainHash, and checksum.
     *
     * @param entry The audit entry to add to the chain
     * @return The entry with computed chain values
     */
    suspend fun addEntryToChain(entry: AuditEntry): AuditEntry = withContext(Dispatchers.IO) {
        val latestHash = auditDao.getLatestChainHash() ?: GENESIS_HASH

        val newEntry = entry.copy(
            chainPrevHash = latestHash,
            chainHash = calculateHash(entry.id, entry.timestamp, latestHash),
            checksum = entry.generateChecksum()
        )

        auditDao.insertOrUpdate(newEntry)
        newEntry
    }

    /**
     * Repairs and recomputes the entire audit chain.
     * Useful after chain corruption or manual database changes.
     * WARNING: This will overwrite existing chain values!
     */
    suspend fun recomputeChain(): ChainRecomputeResult = withContext(Dispatchers.IO) {
        try {
            val entries = auditDao.getAllAuditEntries().first()
            if (entries.isEmpty()) {
                return@withContext ChainRecomputeResult(
                    success = true,
                    entriesProcessed = 0
                )
            }

            val sortedEntries = entries.sortedBy { it.timestamp }
            var prevHash: String? = GENESIS_HASH
            var processedCount = 0

            sortedEntries.forEach { entry ->
                val calculatedChecksum = entry.generateChecksum()
                val updatedEntry = entry.copy(
                    checksum = calculatedChecksum,
                    chainPrevHash = prevHash,
                    chainHash = calculateHash(entry.id, entry.timestamp, prevHash)
                )

                auditDao.update(updatedEntry)
                prevHash = updatedEntry.chainHash
                processedCount++
            }

            ChainRecomputeResult(
                success = true,
                entriesProcessed = processedCount
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to recompute audit chain")
            ChainRecomputeResult(
                success = false,
                entriesProcessed = 0,
                errorMessage = e.message
            )
        }
    }

    /**
     * Calculates cryptographic hash using SHA-256 or SHA-512.
     * Combines entry ID, timestamp, and previous hash to create tamper-evident chain.
     * Optionally includes secret salt for production environments.
     *
     * @param id Entry ID
     * @param timestamp Entry timestamp
     * @param prevHash Previous entry's chain hash (or GENESIS_HASH)
     * @return 64-character (SHA-256) or 128-character (SHA-512) hexadecimal hash string
     */
    private fun calculateHash(id: Long, timestamp: Long, prevHash: String?): String {
        return try {
            if (USE_HMAC) {
                // Use HMAC-SHA256 for stronger security
                calculateHMAC(id, timestamp, prevHash)
            } else {
                // Use standard SHA-256 or SHA-512
                val input = buildString {
                    if (CHAIN_SALT.isNotEmpty()) {
                        append(CHAIN_SALT)
                        append("|")
                    }
                    append(prevHash ?: GENESIS_HASH)
                    append("|")
                    append(id)
                    append("|")
                    append(timestamp)
                }

                val digest = MessageDigest.getInstance(HASH_ALGORITHM)
                val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))

                // Convert byte array to hexadecimal string
                hashBytes.joinToString("") { byte ->
                    "%02x".format(byte)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate hash for entry $id")
            // Fallback to simple hash (not recommended for production)
            val fallbackInput = "${prevHash ?: GENESIS_HASH}|$id|$timestamp"
            fallbackInput.hashCode().toString().padStart(64, '0')
        }
    }

    /**
     * Calculates HMAC-SHA256 for even stronger security.
     * Uses a secret key to create tamper-evident hash that cannot be forged
     * without knowing the secret key.
     *
     * @param id Entry ID
     * @param timestamp Entry timestamp
     * @param prevHash Previous entry's chain hash
     * @return 64-character hexadecimal HMAC-SHA256 hash
     */
    private fun calculateHMAC(id: Long, timestamp: Long, prevHash: String?): String {
        return try {
            val input = buildString {
                if (CHAIN_SALT.isNotEmpty()) {
                    append(CHAIN_SALT)
                    append("|")
                }
                append(prevHash ?: GENESIS_HASH)
                append("|")
                append(id)
                append("|")
                append(timestamp)
            }

            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(HMAC_SECRET_KEY, "HmacSHA256")
            mac.init(secretKey)
            val hashBytes = mac.doFinal(input.toByteArray(Charsets.UTF_8))

            hashBytes.joinToString("") { byte ->
                "%02x".format(byte)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate HMAC for entry $id")
            // Fallback to regular hash
            calculateHash(id, timestamp, prevHash)
        }
    }

    /**
     * Alternative: Calculate hash with additional entropy (optional enhancement)
     * Includes event type and user ID for stronger chain binding.
     * Only use this if you want the hash to depend on entry content.
     */
    private fun calculateHashWithEntropy(entry: AuditEntry, prevHash: String?): String {
        return try {
            val input = buildString {
                if (CHAIN_SALT.isNotEmpty()) {
                    append(CHAIN_SALT)
                    append("|")
                }
                append(prevHash ?: GENESIS_HASH)
                append("|")
                append(entry.id)
                append("|")
                append(entry.timestamp)
                append("|")
                append(entry.eventType.name)
                append("|")
                append(entry.userId ?: "null")
                append("|")
                append(entry.description.take(50)) // First 50 chars of description
            }

            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))

            hashBytes.joinToString("") { byte ->
                "%02x".format(byte)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate entropy hash for entry ${entry.id}")
            calculateHash(entry.id, entry.timestamp, prevHash)
        }
    }

    /**
     * Gets the head (latest) hash of the audit chain
     */
    suspend fun getChainHead(): String = withContext(Dispatchers.IO) {
        auditDao.getLatestChainHash() ?: GENESIS_HASH
    }

    /**
     * Gets statistics about the audit chain
     */
    suspend fun getChainStats(): ChainStats = withContext(Dispatchers.IO) {
        try {
            val totalEntries = auditDao.getTotalEntryCount()
            val entriesWithoutChain = auditDao.countEntriesWithoutChainHash()
            val entriesWithoutChecksum = auditDao.countEntriesWithoutChecksum()

            ChainStats(
                totalEntries = totalEntries,
                entriesWithoutChain = entriesWithoutChain,
                entriesWithoutChecksum = entriesWithoutChecksum,
                chainHealthPercentage = if (totalEntries > 0) {
                    ((totalEntries - entriesWithoutChain) * 100.0 / totalEntries)
                } else {
                    100.0
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get chain stats")
            ChainStats(0, 0, 0, 0.0)
        }
    }
}

// --- Data Classes for Results ---

/**
 * Result of chain verification
 */
data class ChainVerificationResult(
    val isValid: Boolean,
    val totalEntries: Int,
    val errors: List<ChainError>
) {
    val errorCount: Int get() = errors.size
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

/**
 * Represents a single error found during chain verification
 */
data class ChainError(
    val entryId: Long,
    val timestamp: Long,
    val errorType: ChainErrorType,
    val message: String
)

/**
 * Types of chain errors
 */
enum class ChainErrorType {
    CHAIN_BREAK,           // Previous hash doesn't match
    INVALID_HASH,          // Hash computation is incorrect
    CHECKSUM_MISMATCH,     // Entry data has been tampered
    VERIFICATION_FAILED    // System error during verification
}

/**
 * Result of chain recomputation
 */
data class ChainRecomputeResult(
    val success: Boolean,
    val entriesProcessed: Int,
    val errorMessage: String? = null
)

/**
 * Statistics about the audit chain
 */
data class ChainStats(
    val totalEntries: Int,
    val entriesWithoutChain: Int,
    val entriesWithoutChecksum: Int,
    val chainHealthPercentage: Double
)
