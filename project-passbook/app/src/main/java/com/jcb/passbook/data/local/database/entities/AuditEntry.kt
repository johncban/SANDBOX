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

    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "event_type")
    val eventType: AuditEventType,

    @ColumnInfo(name = "user_id")
    val userId: Long? = null,

    val description: String,

    val value: String? = null,

    @ColumnInfo(name = "chain_prev_hash")
    val chainPrevHash: String? = null,

    @ColumnInfo(name = "chain_hash")
    val chainHash: String? = null,

    val checksum: String? = null,

    @ColumnInfo(name = "security_level")
    val securityLevel: String = "NORMAL",

    val outcome: String = "SUCCESS"
) {
    fun generateChecksum(): String {
        val data = "$id$timestamp$eventType$userId$description$value"
        return data.hashCode().toString()
    }
}
