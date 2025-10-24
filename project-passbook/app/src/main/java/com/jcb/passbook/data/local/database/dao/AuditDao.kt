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

    @Query("SELECT * FROM audit_entry WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditEntriesForUser(userId: Int, limit: Int = 1000): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditEntriesByType(eventType: String, limit: Int = 1000): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getAuditEntriesInTimeRange(startTime: Long, endTime: Long): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE outcome = 'FAILURE' OR outcome = 'BLOCKED' ORDER BY timestamp DESC LIMIT :limit")
    fun getFailedAuditEntries(limit: Int = 500): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_entry WHERE securityLevel = 'CRITICAL' ORDER BY timestamp DESC LIMIT :limit")
    fun getCriticalSecurityEvents(limit: Int = 100): Flow<List<AuditEntry>>

    @Query("SELECT COUNT(*) FROM audit_entry WHERE userId = :userId AND eventType = :eventType AND timestamp >= :since")
    suspend fun countEventsSince(userId: Int?, eventType: String, since: Long): Int

    @Query("DELETE FROM audit_entry WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEntries(cutoffTime: Long): Int

    @Query("SELECT * FROM audit_entry ORDER BY timestamp DESC LIMIT :limit")
    fun getAllAuditEntries(limit: Int = 1000): Flow<List<AuditEntry>>

    // Integrity verification queries
    @Query("SELECT COUNT(*) FROM audit_entry WHERE checksum IS NULL")
    suspend fun countEntriesWithoutChecksum(): Int

    @Query("UPDATE audit_entry SET checksum = :checksum WHERE id = :id")
    suspend fun updateChecksum(id: Long, checksum: String)
}