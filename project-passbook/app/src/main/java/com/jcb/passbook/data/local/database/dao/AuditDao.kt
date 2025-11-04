package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.AuditEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    @Insert
    suspend fun insert(auditEntry: AuditEntry): Long

    @Insert
    suspend fun insertAll(auditEntries: List<AuditEntry>)

    @Update
    suspend fun update(auditEntry: AuditEntry)

    @Query("SELECT * FROM audit_entry WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditEntriesForUser(userId: Int, limit: Int = 1000): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditEntriesByType(eventType: String, limit: Int = 1000): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getAuditEntriesInTimeRange(startTime: Long, endTime: Long): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE outcome IN ('FAILURE', 'BLOCKED') ORDER BY timestamp DESC LIMIT :limit")
    fun getFailedAuditEntries(limit: Int = 500): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE securityLevel = 'CRITICAL' ORDER BY timestamp DESC LIMIT :limit")
    fun getCriticalSecurityEvents(limit: Int = 100): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit")
    fun getAuditEntriesForSession(sessionId: String, limit: Int = 1000): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_entry WHERE userId = :userId AND eventType = :eventType AND timestamp >= :since")
    suspend fun countEventsSince(userId: Int?, eventType: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM audit_entry WHERE eventType = :eventType AND timestamp >= :since")
    suspend fun countAllEventsSince(eventType: String, since: Long): Int

    @Query("DELETE FROM audit_entry WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEntries(cutoffTime: Long): Int

    @Query("SELECT * FROM audit_entry ORDER BY timestamp DESC LIMIT :limit")
    fun getAllAuditEntries(limit: Int = 1000): Flow<List<AuditEntry>>

    // Chain integrity queries
    @Query("SELECT COUNT(*) FROM audit_entry WHERE checksum IS NULL")
    suspend fun countEntriesWithoutChecksum(): Int

    @Query("SELECT COUNT(*) FROM audit_entry WHERE chainHash IS NULL")
    suspend fun countEntriesWithoutChainHash(): Int

    @Query("UPDATE audit_entry SET checksum = :checksum WHERE id = :id")
    suspend fun updateChecksum(id: Long, checksum: String)

    @Query("UPDATE audit_entry SET chainPrevHash = :prevHash, chainHash = :chainHash WHERE id = :id")
    suspend fun updateChainHashes(id: Long, prevHash: String, chainHash: String)

    // Advanced search queries
    @Query("""
        SELECT * FROM audit_entry 
        WHERE (:userId IS NULL OR userId = :userId)
        AND (:eventType IS NULL OR eventType = :eventType)
        AND (:outcome IS NULL OR outcome = :outcome)
        AND (:securityLevel IS NULL OR securityLevel = :securityLevel)
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun searchAuditEntries(
        userId: Int? = null,
        eventType: String? = null,
        outcome: String? = null,
        securityLevel: String? = null,
        startTime: Long,
        endTime: Long,
        limit: Int = 1000
    ): Flow<List<AuditEntry>>

    // Statistics queries
    @Query("SELECT eventType, COUNT(*) as count FROM audit_entry WHERE timestamp >= :since GROUP BY eventType")
    suspend fun getEventTypeStatistics(since: Long): List<EventTypeCount>

    @Query("SELECT outcome, COUNT(*) as count FROM audit_entry WHERE timestamp >= :since GROUP BY outcome")
    suspend fun getOutcomeStatistics(since: Long): List<OutcomeCount>

    @Query("SELECT securityLevel, COUNT(*) as count FROM audit_entry WHERE timestamp >= :since GROUP BY securityLevel")
    suspend fun getSecurityLevelStatistics(since: Long): List<SecurityLevelCount>

    // Maintenance queries
    @Query("SELECT id, checksum FROM audit_entry WHERE checksum IS NOT NULL ORDER BY timestamp ASC")
    suspend fun getAllChecksums(): List<ChecksumInfo>

    @Query("SELECT id, chainPrevHash, chainHash FROM audit_entry WHERE chainHash IS NOT NULL ORDER BY timestamp ASC")
    suspend fun getAllChainHashes(): List<ChainInfo>

    @Query("SELECT MAX(timestamp) FROM audit_entry")
    suspend fun getLatestEntryTimestamp(): Long?

    @Query("SELECT MIN(timestamp) FROM audit_entry")
    suspend fun getOldestEntryTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM audit_entry")
    suspend fun getTotalEntryCount(): Long
}

// Data classes for query results
data class EventTypeCount(val eventType: String, val count: Int)
data class OutcomeCount(val outcome: String, val count: Int)
data class SecurityLevelCount(val securityLevel: String, val count: Int)
data class ChecksumInfo(val id: Long, val checksum: String?)
data class ChainInfo(val id: Long, val chainPrevHash: String?, val chainHash: String?)