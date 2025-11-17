package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.models.ChainInfo
import com.jcb.passbook.data.local.database.models.ChecksumInfo
import com.jcb.passbook.data.local.database.models.EventTypeCount
import com.jcb.passbook.data.local.database.models.OutcomeCount
import com.jcb.passbook.data.local.database.models.SecurityLevelCount
import kotlinx.coroutines.flow.Flow

/**
 * AuditDao - Data Access Object for audit log operations
 * 
 * COMPLETE REVISED VERSION - November 13, 2025
 * - Removed all duplicate methods
 * - Added all missing methods from error logs
 * - Fixed all query signatures and return types
 * - Organized methods by category
 */
@Dao
interface AuditDao {
    
    // =====================================================
    // INSERT OPERATIONS
    // =====================================================
    
    /**
     * Insert a single audit entry
     * @return The row ID of the inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditEntry): Long
    
    /**
     * Insert multiple audit entries in a batch
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AuditEntry>)
    
    /**
     * Insert or update an audit entry (upsert)
     */
    @Upsert
    suspend fun insertOrUpdate(entry: AuditEntry)
    
    
    // =====================================================
    // UPDATE OPERATIONS
    // =====================================================
    
    /**
     * Update an existing audit entry
     */
    @Update
    suspend fun update(entry: AuditEntry)
    
    
    // =====================================================
    // DELETE OPERATIONS
    // =====================================================
    
    /**
     * Delete audit entries older than the specified timestamp
     * @param olderThan Timestamp threshold
     * @return Number of deleted entries
     */
    @Query("DELETE FROM audit_log WHERE timestamp < :olderThan")
    suspend fun deleteOldEntries(olderThan: Long): Int
    
    /**
     * Delete all audit entries (use with caution!)
     */
    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()
    
    
    // =====================================================
    // QUERY OPERATIONS - BASIC RETRIEVAL
    // =====================================================
    
    /**
     * Get all audit entries ordered by timestamp (newest first)
     */
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    fun getAllAuditEntries(): Flow<List<AuditEntry>>
    
    /**
     * Get the most recent audit entries with a limit
     * @param limit Maximum number of entries to return
     */
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<AuditEntry>>
    
    /**
     * Get a single audit entry by ID
     * @param id The entry ID
     * @return The audit entry or null if not found
     */
    @Query("SELECT * FROM audit_log WHERE id = :id")
    suspend fun getEntryById(id: Long): AuditEntry?
    
    /**
     * Get the latest audit entry
     * @return The most recent entry or null if no entries exist
     */
    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun getLatestEntry(): AuditEntry?
    
    
    // =====================================================
    // QUERY OPERATIONS - FILTERED RETRIEVAL
    // =====================================================
    
    /**
     * Get all audit entries for a specific user
     * @param userId The user ID
     */
    @Query("SELECT * FROM audit_log WHERE user_id = :userId ORDER BY timestamp DESC")
    fun getAuditEntriesForUser(userId: Long): Flow<List<AuditEntry>>
    
    /**
     * Get all audit entries of a specific event type
     * @param eventType The event type enum
     */
    @Query("SELECT * FROM audit_log WHERE event_type = :eventType ORDER BY timestamp DESC")
    fun getAuditEntriesByType(eventType: AuditEventType): Flow<List<AuditEntry>>
    
    /**
     * Get all failed audit entries (FAILURE or ERROR outcomes)
     */
    @Query("SELECT * FROM audit_log WHERE outcome = 'FAILURE' OR outcome = 'ERROR' ORDER BY timestamp DESC")
    fun getFailedAuditEntries(): Flow<List<AuditEntry>>
    
    /**
     * Get all critical security events (CRITICAL or HIGH security levels)
     */
    @Query("SELECT * FROM audit_log WHERE security_level = 'CRITICAL' OR security_level = 'HIGH' ORDER BY timestamp DESC")
    fun getCriticalSecurityEvents(): Flow<List<AuditEntry>>
    
    /**
     * Get audit entries within a time range
     * @param startTime Start of time range
     * @param endTime End of time range
     */
    @Query("SELECT * FROM audit_log WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getAuditEntriesInTimeRange(startTime: Long, endTime: Long): Flow<List<AuditEntry>>
    
    /**
     * Get audit entries for a specific user within a time range
     * @param userId The user ID
     * @param startTime Start of time range
     * @param endTime End of time range
     */
    @Query("""
        SELECT * FROM audit_log 
        WHERE user_id = :userId 
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun getUserEntriesInTimeRange(
        userId: Long,
        startTime: Long,
        endTime: Long
    ): Flow<List<AuditEntry>>
    
    /**
     * Search audit entries with multiple optional filters
     * @param userId Optional user ID filter
     * @param eventType Optional event type filter (as String)
     * @param outcome Optional outcome filter
     * @param securityLevel Optional security level filter
     * @param startTime Start of time range
     * @param endTime End of time range
     */
    @Query("""
        SELECT * FROM audit_log 
        WHERE (:userId IS NULL OR user_id = :userId)
        AND (:eventType IS NULL OR event_type = :eventType)
        AND (:outcome IS NULL OR outcome = :outcome)
        AND (:securityLevel IS NULL OR security_level = :securityLevel)
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun searchAuditEntries(
        userId: Long? = null,
        eventType: String? = null,
        outcome: String? = null,
        securityLevel: String? = null,
        startTime: Long,
        endTime: Long
    ): Flow<List<AuditEntry>>
    
    
    // =====================================================
    // QUERY OPERATIONS - STATISTICS & COUNTS
    // =====================================================
    
    /**
     * Get total count of all audit entries
     */
    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun getTotalCount(): Int
    
    /**
     * Get total entry count (alias for getTotalCount)
     */
    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun getTotalEntryCount(): Long
    
    /**
     * Get count of audit entries for a specific user
     * @param userId The user ID
     */
    @Query("SELECT COUNT(*) FROM audit_log WHERE user_id = :userId")
    suspend fun getCountByUser(userId: Long): Int
    
    /**
     * Count entries without checksum
     */
    @Query("SELECT COUNT(*) FROM audit_log WHERE checksum IS NULL")
    suspend fun countEntriesWithoutChecksum(): Int
    
    /**
     * Count entries without chain hash
     */
    @Query("SELECT COUNT(*) FROM audit_log WHERE chain_hash IS NULL")
    suspend fun countEntriesWithoutChainHash(): Int
    
    /**
     * Count unique users in audit log
     */
    @Query("SELECT COUNT(DISTINCT user_id) FROM audit_log")
    suspend fun getUniqueUserCount(): Int
    
    /**
     * Count all events since a specific timestamp
     * @param since Timestamp to count from
     */
    @Query("SELECT COUNT(*) FROM audit_log WHERE timestamp >= :since")
    suspend fun countAllEventsSince(since: Long): Int
    
    /**
     * Get oldest entry timestamp
     */
    @Query("SELECT timestamp FROM audit_log ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestEntryTimestamp(): Long?
    
    /**
     * Get latest entry timestamp
     */
    @Query("SELECT timestamp FROM audit_log ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntryTimestamp(): Long?
    
    
    // =====================================================
    // QUERY OPERATIONS - AGGREGATED DATA
    // =====================================================
    
    /**
     * Get event type distribution statistics
     * @return Flow of event type counts
     */
    @Query("SELECT event_type AS eventType, COUNT(*) as count FROM audit_log GROUP BY event_type")
    fun getEventTypeCounts(): Flow<List<EventTypeCount>>
    
    /**
     * Get outcome distribution statistics
     * @return Flow of outcome counts
     */
    @Query("SELECT outcome, COUNT(*) as count FROM audit_log GROUP BY outcome")
    fun getOutcomeCounts(): Flow<List<OutcomeCount>>
    
    /**
     * Get security level distribution statistics
     * @return Flow of security level counts
     */
    @Query("SELECT security_level AS securityLevel, COUNT(*) as count FROM audit_log GROUP BY security_level")
    fun getSecurityLevelCounts(): Flow<List<SecurityLevelCount>>
    
    
    // =====================================================
    // QUERY OPERATIONS - CHAIN & SECURITY
    // =====================================================
    
    /**
     * Get all chain information for verification
     * @return Flow of chain info for all entries
     */
    @Query("SELECT id, chain_prev_hash AS chainPrevHash, chain_hash AS chainHash FROM audit_log ORDER BY timestamp ASC")
    fun getAllChainInfo(): Flow<List<ChainInfo>>
    
    /**
     * Get all checksums for verification
     * @return Flow of checksum info for all entries with checksums
     */
    @Query("SELECT id, checksum FROM audit_log WHERE checksum IS NOT NULL")
    fun getAllChecksums(): Flow<List<ChecksumInfo>>
    
    /**
     * Get the latest chain hash for chain continuation
     * @return The most recent chain hash or null
     */
    @Query("SELECT chain_hash FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun getLatestChainHash(): String?
}
