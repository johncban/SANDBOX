package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AuditMetadata entity stores metadata about the audit system,
 * including chain head hashes and system configuration.
 */
@Entity(tableName = "audit_metadata")
data class AuditMetadata(
    @PrimaryKey
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String? = null
)