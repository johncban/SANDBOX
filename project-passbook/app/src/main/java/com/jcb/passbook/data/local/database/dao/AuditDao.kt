package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.AuditEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(auditEntry: AuditEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(auditEntries: List<AuditEntry>)

    @Query("SELECT * FROM audit_entries WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditEntriesForUser(userId: Int, limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entries WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditEntriesByType(eventType: String, limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entries WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getAuditEntriesInTimeRange(startTime: Long, endTime: Long): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entries WHERE outcome = 'FAILURE' ORDER BY timestamp DESC LIMIT :limit")
    fun getFailedAuditEntries(limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entries WHERE eventType = 'SECURITY_EVENT' ORDER BY timestamp DESC LIMIT :limit")
    fun getCriticalSecurityEvents(limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_entries WHERE (:userId IS NULL OR userId = :userId) AND eventType = :eventType AND timestamp >= :since")
    suspend fun countEventsSince(userId: Int?, eventType: String, since: Long): Int

    @Query("DELETE FROM audit_entries WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEntries(cutoffTime: Long): Int

    @Query("SELECT * FROM audit_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getAllAuditEntries(limit: Int): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_entries WHERE checksum IS NULL")
    suspend fun countEntriesWithoutChecksum(): Int

    @Query("UPDATE audit_entries SET checksum = :checksum WHERE id = :id")
    suspend fun updateChecksum(id: Long, checksum: String)
}