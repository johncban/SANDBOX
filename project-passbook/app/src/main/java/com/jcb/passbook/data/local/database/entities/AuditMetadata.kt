package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * AuditMetadata Entity - Stores audit system metadata
 *
 * Used for tracking audit chain integrity, configuration, and system state
 */
@Entity(tableName = "audit_metadata")
data class AuditMetadata(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "description")
    val description: String? = null
)

/**
 * Common metadata keys for audit system
 */
object AuditMetadataKeys {
    const val AUDIT_CHAIN_HEAD = "audit_chain_head"
    const val LAST_VERIFICATION = "last_verification"
    const val TOTAL_ENTRIES = "total_entries"
    const val CHAIN_INTEGRITY = "chain_integrity"
    const val SYSTEM_VERSION = "system_version"
}