package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.models.ChainInfo
import com.jcb.passbook.data.local.database.models.ChecksumInfo
import com.jcb.passbook.data.local.database.models.EventTypeCount
import com.jcb.passbook.data.local.database.models.OutcomeCount
import com.jcb.passbook.data.local.database.models.SecurityLevelCount
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditEntry): Long

    @Upsert
    suspend fun insertOrUpdate(entry: AuditEntry)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log WHERE user_id = :userId ORDER BY timestamp DESC")
    fun getEntriesByUser(userId: Long): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log WHERE event_type = :eventType ORDER BY timestamp DESC")
    fun getEntriesByEventType(eventType: String): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getEntriesByTimeRange(startTime: Long, endTime: Long): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log WHERE outcome = :outcome ORDER BY timestamp DESC")
    fun getEntriesByOutcome(outcome: String): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log WHERE security_level = :securityLevel ORDER BY timestamp DESC")
    fun getEntriesBySecurityLevel(securityLevel: String): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM audit_log WHERE user_id = :userId")
    suspend fun getCountByUser(userId: Long): Int

    @Query("DELETE FROM audit_log WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()

    // ✅ FIXED: Changed query to use aliases matching @ColumnInfo
    @Query("SELECT id, chain_prev_hash AS chainPrevHash, chain_hash AS chainHash FROM audit_log ORDER BY timestamp ASC")
    fun getAllChainInfo(): Flow<List<ChainInfo>>

    @Query("SELECT id, checksum FROM audit_log WHERE checksum IS NOT NULL")
    fun getAllChecksums(): Flow<List<ChecksumInfo>>

    @Query("SELECT * FROM audit_log WHERE id = :id")
    suspend fun getEntryById(id: Long): AuditEntry?

    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun getLatestEntry(): AuditEntry?

    @Query("SELECT chain_hash FROM audit_log ORDER BY id DESC LIMIT 1")
    suspend fun getLatestChainHash(): String?

    // ✅ FIXED: Changed query to use alias matching @ColumnInfo
    @Query("SELECT event_type AS eventType, COUNT(*) as count FROM audit_log GROUP BY event_type")
    fun getEventTypeCounts(): Flow<List<EventTypeCount>>

    @Query("SELECT outcome, COUNT(*) as count FROM audit_log GROUP BY outcome")
    fun getOutcomeCounts(): Flow<List<OutcomeCount>>

    // ✅ FIXED: Changed query to use alias matching @ColumnInfo
    @Query("SELECT security_level AS securityLevel, COUNT(*) as count FROM audit_log GROUP BY security_level")
    fun getSecurityLevelCounts(): Flow<List<SecurityLevelCount>>

    @Query("SELECT * FROM audit_log WHERE user_id = :userId AND timestamp BETWEEN :startTime AND :endTime")
    fun getUserEntriesInTimeRange(
        userId: Long,
        startTime: Long,
        endTime: Long
    ): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(DISTINCT user_id) FROM audit_log")
    suspend fun getUniqueUserCount(): Int

    @Query("""
        SELECT * FROM audit_log 
        WHERE (:userId IS NULL OR user_id = :userId)
        AND (:eventType IS NULL OR event_type = :eventType)
        AND (:outcome IS NULL OR outcome = :outcome)
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun getFilteredEntries(
        userId: Long?,
        eventType: String?,
        outcome: String?,
        startTime: Long,
        endTime: Long
    ): Flow<List<AuditEntry>>

    // ✅ REMOVED: This query was causing Map<AuditEventType, Int> errors
    // Use getEventTypeCounts() instead which returns List<EventTypeCount>
}
