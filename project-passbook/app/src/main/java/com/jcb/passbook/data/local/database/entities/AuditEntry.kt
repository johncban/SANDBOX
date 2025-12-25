package com.jcb.passbook.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["event_type"]),
        Index(value = ["user_id"])
    ]
)
data class AuditEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "user_id")
    val userId: Long? = null,
    val username: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "event_type")
    val eventType: AuditEventType,
    val action: String? = null,
    val description: String,
    val value: String? = null,
    @ColumnInfo(name = "resource_type")
    val resourceType: String? = null,
    @ColumnInfo(name = "resource_id")
    val resourceId: String? = null,
    @ColumnInfo(name = "device_info")
    val deviceInfo: String? = null,
    @ColumnInfo(name = "app_version")
    val appVersion: String? = null,
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,
    val outcome: AuditOutcome = AuditOutcome.SUCCESS, // *** CHANGED TO ENUM! ***
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "security_level")
    val securityLevel: String = "NORMAL",
    @ColumnInfo(name = "ip_address")
    val ipAddress: String? = null,
    @ColumnInfo(name = "user_agent")
    val userAgent: String? = null,
    val location: String? = null,
    @ColumnInfo(name = "chain_prev_hash")
    val chainPrevHash: String? = null,
    @ColumnInfo(name = "chain_hash")
    val chainHash: String? = null,
    val checksum: String? = null
) {
    fun generateChecksum(): String {
        val data = "$id$timestamp$eventType$userId$description${value ?: ""}"
        return data.hashCode().toString()
    }
}
