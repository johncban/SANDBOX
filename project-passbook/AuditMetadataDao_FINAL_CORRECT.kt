package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.jcb.passbook.data.local.database.entities.AuditMetadata
import kotlinx.coroutines.flow.Flow

/**
 * AuditMetadataDao - Data Access Object for AuditMetadata entity
 * Manages audit system metadata including chain head tracking and verification status
 */
@Dao
interface AuditMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: AuditMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<AuditMetadata>)

    @Update
    suspend fun update(metadata: AuditMetadata)

    @Delete
    suspend fun delete(metadata: AuditMetadata)

    @Query("SELECT * FROM audit_metadata WHERE key = :key")
    fun getMetadata(key: String): Flow<AuditMetadata?>

    @Query("SELECT * FROM audit_metadata WHERE key = :key")
    suspend fun getMetadataSync(key: String): AuditMetadata?

    @Query("SELECT value FROM audit_metadata WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM audit_metadata ORDER BY timestamp DESC")
    fun getAllMetadata(): Flow<List<AuditMetadata>>

    @Query("UPDATE audit_metadata SET value = :value, timestamp = :timestamp WHERE key = :key")
    suspend fun updateValue(key: String, value: String, timestamp: Long)

    @Query("DELETE FROM audit_metadata WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM audit_metadata")
    suspend fun deleteAll()

    @Query("SELECT value FROM audit_metadata WHERE key = 'audit_chain_head'")
    suspend fun getChainHead(): String?

    @Query("UPDATE audit_metadata SET value = :hash, timestamp = :timestamp WHERE key = 'audit_chain_head'")
    suspend fun updateChainHead(hash: String, timestamp: Long)
}

// Extension functions OUTSIDE the interface
suspend fun AuditMetadataDao.updateValue(key: String, value: String) {
    updateValue(key, value, System.currentTimeMillis())
}

suspend fun AuditMetadataDao.updateChainHead(hash: String) {
    updateChainHead(hash, System.currentTimeMillis())
}
