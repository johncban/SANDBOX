package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.AuditMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: AuditMetadata)

    @Query("SELECT * FROM audit_metadata WHERE key = :key")
    suspend fun getMetadata(key: String): AuditMetadata?

    @Query("SELECT * FROM audit_metadata ORDER BY timestamp DESC")
    fun getAllMetadata(): Flow<List<AuditMetadata>>

    @Query("DELETE FROM audit_metadata WHERE key = :key")
    suspend fun deleteMetadata(key: String)

    @Query("SELECT value FROM audit_metadata WHERE key = :key")
    suspend fun getMetadataValue(key: String): String?

    @Query("UPDATE audit_metadata SET value = :value, timestamp = :timestamp WHERE key = :key")
    suspend fun updateMetadataValue(key: String, value: String, timestamp: Long = System.currentTimeMillis())
}