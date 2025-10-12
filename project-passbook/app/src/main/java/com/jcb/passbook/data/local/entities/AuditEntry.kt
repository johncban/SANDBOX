package com.jcb.passbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_entry")
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int?,
    val username: String?,
    val timestamp: Long,
    val eventType: String,
    val action: String,
    val resourceType: String?,
    val resourceId: String?,
    val deviceInfo: String?,
    val appVersion: String?,
    val sessionId: String?,
    val outcome: String,
    val errorMessage: String?,
    val securityLevel: String = "NORMAL",
    val ipAddress: String?,
    val checksum: String?
)