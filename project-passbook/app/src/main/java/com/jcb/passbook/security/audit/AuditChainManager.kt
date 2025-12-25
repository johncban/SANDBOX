package com.jcb.passbook.security.audit

import android.content.Context
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

@Singleton
class AuditChainManager @Inject constructor(
    context: Context,
    private val auditDao: AuditDao
) {
    companion object {
        private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val HASH_ALGORITHM = "SHA-256" // Or SHA-512 if you prefer
        private const val CHAIN_SALT = ""
        private const val USE_HMAC = false
        private val HMAC_SECRET_KEY = "your-hmac-secret-key-here".toByteArray(Charsets.UTF_8)
    }

    suspend fun verifyChain(): ChainVerificationResult = withContext(Dispatchers.IO) {
        try {
            val entries = auditDao.getAllAuditEntries().first()
            if (entries.isEmpty()) {
                return@withContext ChainVerificationResult(true, 0, emptyList())
            }

            var prevHash: String? = GENESIS_HASH
            val errors = mutableListOf<ChainError>()

            entries.sortedBy { it.timestamp }.forEachIndexed { index: Int, entry ->
                // 1. Chain linkage
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
                // 2. Hash correctness
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
                // 3. Checksum validation
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

    suspend fun recomputeChain(): ChainRecomputeResult = withContext(Dispatchers.IO) {
        try {
            val entries = auditDao.getAllAuditEntries().first()
            if (entries.isEmpty()) return@withContext ChainRecomputeResult(true, 0)

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
            ChainRecomputeResult(true, processedCount)
        } catch (e: Exception) {
            Timber.e(e, "Failed to recompute audit chain")
            ChainRecomputeResult(false, 0, e.message)
        }
    }

    private fun calculateHash(id: Long, timestamp: Long, prevHash: String?): String {
        return try {
            if (USE_HMAC) {
                calculateHMAC(id, timestamp, prevHash)
            } else {
                val input = buildString {
                    if (CHAIN_SALT.isNotEmpty()) {
                        append(CHAIN_SALT).append("|")
                    }
                    append(prevHash ?: GENESIS_HASH).append("|")
                    append(id).append("|")
                    append(timestamp)
                }
                val digest = MessageDigest.getInstance(HASH_ALGORITHM)
                val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
                hashBytes.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate hash for entry $id")
            val fallbackInput = "${prevHash ?: GENESIS_HASH}|$id|$timestamp"
            fallbackInput.hashCode().toString().padStart(64, '0')
        }
    }

    private fun calculateHMAC(id: Long, timestamp: Long, prevHash: String?): String {
        return try {
            val input = buildString {
                if (CHAIN_SALT.isNotEmpty()) append(CHAIN_SALT).append("|")
                append(prevHash ?: GENESIS_HASH).append("|")
                append(id).append("|")
                append(timestamp)
            }
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(HMAC_SECRET_KEY, "HmacSHA256")
            mac.init(secretKey)
            val hashBytes = mac.doFinal(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate HMAC for entry $id")
            calculateHash(id, timestamp, prevHash)
        }
    }

    // --- Data Classes for Results ---

    data class ChainVerificationResult(
        val isValid: Boolean,
        val totalEntries: Int,
        val errors: List<ChainError>
    ) {
        val errorCount: Int get() = errors.size
        val hasErrors: Boolean get() = errors.isNotEmpty()
    }

    data class ChainError(
        val entryId: Long,
        val timestamp: Long,
        val errorType: ChainErrorType,
        val message: String
    )

    enum class ChainErrorType {
        CHAIN_BREAK,
        INVALID_HASH,
        CHECKSUM_MISMATCH,
        VERIFICATION_FAILED
    }

    data class ChainRecomputeResult(
        val success: Boolean,
        val entriesProcessed: Int,
        val errorMessage: String? = null
    )
}
