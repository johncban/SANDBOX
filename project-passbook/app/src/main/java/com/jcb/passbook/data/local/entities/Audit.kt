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
    val eventType: String, // LOGIN, LOGOUT, CREATE, READ, UPDATE, DELETE, SECURITY_EVENT
    val action: String,    // Detailed description
    val resourceType: String? = null, // USER, ITEM, SYSTEM
    val resourceId: String? = null,   // Resource identifier

    // Context information
    val deviceInfo: String? = null,   // Device model, OS version
    val appVersion: String? = null,   // App version
    val sessionId: String? = null,    // Session identifier

    // Outcome
    val outcome: String, // SUCCESS, FAILURE, WARNING
    val errorMessage: String? = null,

    // Security context
    val securityLevel: String = "NORMAL", // NORMAL, ELEVATED, CRITICAL
    val ipAddress: String? = null,        // For future network features

    // Integrity protection
    val checksum: String? = null  // SHA-256 hash for tampering detection
) {
    // Generate integrity checksum
    fun generateChecksum(): String {
        val data = "$userId$timestamp$eventType$action$resourceType$resourceId$outcome"
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

enum class AuditEventType(val value: String) {
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT"),
    REGISTER("REGISTER"),
    CREATE("CREATE"),
    READ("READ"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    SECURITY_EVENT("SECURITY_EVENT"),
    SYSTEM_EVENT("SYSTEM_EVENT"),
    KEY_ROTATION("KEY_ROTATION"),
    AUTHENTICATION_FAILURE("AUTH_FAILURE")
}

enum class AuditOutcome(val value: String) {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    WARNING("WARNING"),
    BLOCKED("BLOCKED")
}