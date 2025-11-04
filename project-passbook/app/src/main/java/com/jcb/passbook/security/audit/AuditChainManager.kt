package com.jcb.passbook.security.audit

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditMetadata
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditChainManager manages tamper-evident chaining of audit entries.
 * Each entry is linked to the previous one via cryptographic hashes,
 * making unauthorized deletion or modification detectable.
 */
@Singleton
class AuditChainManager @Inject constructor(
    private val auditDao: AuditDao,
    private val auditMetadataDao: AuditMetadataDao,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    companion object {
        private const val CHAIN_METADATA_KEY = "audit_chain_head"
        private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val TAG = "AuditChainManager"
    }

    private val digest = MessageDigest.getInstance("SHA-256")

    /**
     * Add an audit entry to the tamper-evident chain
     */
    suspend fun addToChain(entry: AuditEntry): AuditEntry {
        return withContext(Dispatchers.IO) {
            try {
                // Get the previous chain hash
                val prevHash = getChainHead() ?: GENESIS_HASH

                // Create the canonical entry data for hashing (excluding chain fields)
                val canonicalData = createCanonicalEntryData(entry)

                // Compute the chain hash: SHA-256(prevHash + canonicalData)
                val chainInput = (prevHash + canonicalData).toByteArray(Charsets.UTF_8)
                val chainHash = digest.digest(chainInput).joinToString("") { "%02x".format(it) }

                // Compute the entry checksum
                val checksum = entry.generateChecksum()

                // Create the chained entry
                val chainedEntry = entry.copy(
                    chainPrevHash = prevHash,
                    chainHash = chainHash,
                    checksum = checksum
                )

                // Update chain head
                updateChainHead(chainHash)

                Timber.v("Added entry to audit chain: ${chainHash.take(8)}...")
                chainedEntry

            } catch (e: Exception) {
                Timber.e(e, "Failed to add entry to audit chain")
                // Return entry with basic checksum if chaining fails
                entry.copy(checksum = entry.generateChecksum())
            }
        }
    }

    /**
     * Verify the integrity of the audit chain
     */
    suspend fun verifyChain(startTime: Long? = null, endTime: Long? = null): ChainVerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                val entries = if (startTime != null && endTime != null) {
                    auditDao.getAuditEntriesInTimeRange(startTime, endTime).value
                } else {
                    auditDao.getAllAuditEntries(10000).value // Get a large batch
                }

                if (entries.isEmpty()) {
                    return@withContext ChainVerificationResult.Success(0, emptyList())
                }

                val discrepancies = mutableListOf<ChainDiscrepancy>()
                var verifiedCount = 0
                var expectedPrevHash = GENESIS_HASH

                entries.sortedBy { it.timestamp }.forEach { entry ->
                    // Verify previous hash linkage
                    if (entry.chainPrevHash != expectedPrevHash) {
                        discrepancies.add(
                            ChainDiscrepancy.BrokenChain(
                                entry.id,
                                entry.timestamp,
                                "Expected prevHash: $expectedPrevHash, found: ${entry.chainPrevHash}"
                            )
                        )
                    }

                    // Verify chain hash computation
                    val canonicalData = createCanonicalEntryData(entry)
                    val expectedChainHash = computeChainHash(entry.chainPrevHash ?: "", canonicalData)

                    if (entry.chainHash != expectedChainHash) {
                        discrepancies.add(
                            ChainDiscrepancy.InvalidHash(
                                entry.id,
                                entry.timestamp,
                                "Chain hash mismatch: expected $expectedChainHash, found ${entry.chainHash}"
                            )
                        )
                    }

                    // Verify entry checksum
                    val expectedChecksum = entry.generateChecksum()
                    if (entry.checksum != expectedChecksum) {
                        discrepancies.add(
                            ChainDiscrepancy.InvalidChecksum(
                                entry.id,
                                entry.timestamp,
                                "Checksum mismatch: expected $expectedChecksum, found ${entry.checksum}"
                            )
                        )
                    }

                    expectedPrevHash = entry.chainHash ?: ""
                    verifiedCount++
                }

                if (discrepancies.isEmpty()) {
                    ChainVerificationResult.Success(verifiedCount, emptyList())
                } else {
                    ChainVerificationResult.Compromised(verifiedCount, discrepancies)
                }

            } catch (e: Exception) {
                Timber.e(e, "Chain verification failed")
                ChainVerificationResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Get the current chain head hash
     */
    private suspend fun getChainHead(): String? {
        return try {
            auditMetadataDao.getMetadata(CHAIN_METADATA_KEY)?.value
        } catch (e: Exception) {
            Timber.e(e, "Failed to get chain head")
            null
        }
    }

    /**
     * Update the chain head hash
     */
    private suspend fun updateChainHead(chainHash: String) {
        try {
            val metadata = AuditMetadata(
                key = CHAIN_METADATA_KEY,
                value = chainHash,
                timestamp = System.currentTimeMillis()
            )
            auditMetadataDao.insertOrUpdate(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update chain head")
        }
    }

    /**
     * Create canonical entry data for hashing (excludes chain fields and id)
     */
    private fun createCanonicalEntryData(entry: AuditEntry): String {
        return buildString {
            append("userId:${entry.userId ?: "null"};")
            append("username:${entry.username ?: "null"};")
            append("timestamp:${entry.timestamp};")
            append("eventType:${entry.eventType};")
            append("action:${entry.action};")
            append("resourceType:${entry.resourceType ?: "null"};")
            append("resourceId:${entry.resourceId ?: "null"};")
            append("deviceInfo:${entry.deviceInfo ?: "null"};")
            append("appVersion:${entry.appVersion ?: "null"};")
            append("sessionId:${entry.sessionId ?: "null"};")
            append("outcome:${entry.outcome};")
            append("errorMessage:${entry.errorMessage ?: "null"};")
            append("securityLevel:${entry.securityLevel};")
            append("ipAddress:${entry.ipAddress ?: "null"};")
        }
    }

    /**
     * Compute chain hash from previous hash and canonical data
     */
    private fun computeChainHash(prevHash: String, canonicalData: String): String {
        val chainInput = (prevHash + canonicalData).toByteArray(Charsets.UTF_8)
        return digest.digest(chainInput).joinToString("") { "%02x".format(it) }
    }

    /**
     * Repair chain by recomputing hashes (use with caution - only for legitimate recovery)
     */
    suspend fun repairChain(startTime: Long, endTime: Long): ChainRepairResult {
        return withContext(Dispatchers.IO) {
            try {
                val entries = auditDao.getAuditEntriesInTimeRange(startTime, endTime).value
                    .sortedBy { it.timestamp }

                if (entries.isEmpty()) {
                    return@withContext ChainRepairResult.NoEntriesFound
                }

                var prevHash = entries.firstOrNull()?.chainPrevHash ?: GENESIS_HASH
                var repairedCount = 0

                entries.forEach { entry ->
                    val canonicalData = createCanonicalEntryData(entry)
                    val newChainHash = computeChainHash(prevHash, canonicalData)
                    val newChecksum = entry.generateChecksum()

                    val repairedEntry = entry.copy(
                        chainPrevHash = prevHash,
                        chainHash = newChainHash,
                        checksum = newChecksum
                    )

                    auditDao.update(repairedEntry)
                    prevHash = newChainHash
                    repairedCount++
                }

                // Update chain head
                updateChainHead(prevHash)

                ChainRepairResult.Success(repairedCount)

            } catch (e: Exception) {
                Timber.e(e, "Chain repair failed")
                ChainRepairResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Chain verification results
     */
    sealed class ChainVerificationResult {
        data class Success(val verifiedEntries: Int, val warnings: List<String>) : ChainVerificationResult()
        data class Compromised(val verifiedEntries: Int, val discrepancies: List<ChainDiscrepancy>) : ChainVerificationResult()
        data class Error(val message: String) : ChainVerificationResult()
    }

    /**
     * Chain repair results
     */
    sealed class ChainRepairResult {
        data class Success(val repairedEntries: Int) : ChainRepairResult()
        object NoEntriesFound : ChainRepairResult()
        data class Error(val message: String) : ChainRepairResult()
    }

    /**
     * Types of chain discrepancies
     */
    sealed class ChainDiscrepancy {
        data class BrokenChain(val entryId: Long, val timestamp: Long, val details: String) : ChainDiscrepancy()
        data class InvalidHash(val entryId: Long, val timestamp: Long, val details: String) : ChainDiscrepancy()
        data class InvalidChecksum(val entryId: Long, val timestamp: Long, val details: String) : ChainDiscrepancy()
    }
}