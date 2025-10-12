package com.jcb.passbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.security.MessageDigest

@Entity(
    tableName = "audit_entry",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId"], name = "index_audit_entry_userId"),
        Index(value = ["timestamp"], name = "index_audit_entry_timestamp"),
        Index(value = ["eventType"], name = "index_audit_entry_eventType"),
        Index(value = ["outcome"], name = "index_audit_entry_outcome")
    ]
)
data class Audit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // User identification (nullable for system events)
    val userId: Int? = null,
    val username: String? = null,

    // Timestamp (UTC milliseconds since epoch)
    val timestamp: Long = System.currentTimeMillis(),

    // Action details
    val eventType: AuditEventType, // USE ENUM TYPE, NOT STRING
    val action: String,    // Detailed description
    val resourceType: String? = null, // USER, ITEM, SYSTEM
    val resourceId: String? = null,   // Resource identifier

    // Context information
    val deviceInfo: String? = null,
    val appVersion: String? = null,
    val sessionId: String? = null,

    // Outcome
    val outcome: AuditOutcome, // USE ENUM TYPE, NOT STRING
    val errorMessage: String? = null,

    // Security context
    val securityLevel: SecurityLevel = SecurityLevel.NORMAL,
    val ipAddress: String? = null,

    // Integrity protection
    val checksum: String? = null  // SHA-256 hash for tampering detection
) {
    fun generateChecksum(): String {
        val data = "$userId$timestamp$eventType$action$resourceType$resourceId$outcome"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
