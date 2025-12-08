package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLogEntity>): List<Long>

    @Query("SELECT * FROM audit_logs WHERE log_id = :logId")
    suspend fun getAuditLogById(logId: Long): AuditLogEntity?

    @Query("SELECT * FROM audit_logs WHERE user_id = :userId ORDER BY timestamp DESC")
    fun getAuditLogsByUserId(userId: Long): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE user_id = :userId AND event_type = :eventType ORDER BY timestamp DESC")
    fun getAuditLogsByType(userId: Long, eventType: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE user_id = :userId AND success = 0 ORDER BY timestamp DESC")
    fun getFailedEvents(userId: Long): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE user_id = :userId AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getAuditLogsByTimeRange(userId: Long, startTime: Long, endTime: Long): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_logs WHERE user_id = :userId")
    suspend fun deleteAuditLogsForUser(userId: Long)

    @Query("DELETE FROM audit_logs WHERE timestamp < :timestamp")
    suspend fun deleteOldLogs(timestamp: Long)

    @Query("SELECT COUNT(*) FROM audit_logs WHERE user_id = :userId")
    suspend fun getAuditLogCount(userId: Long): Int

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<AuditLogEntity>>
}
