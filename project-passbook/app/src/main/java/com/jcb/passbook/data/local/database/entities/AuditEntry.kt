package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

/**
 * AuditEntry Entity - Tracks all user actions for security auditing
 *
 * FIXES APPLIED:
 * - Added Index on userId foreign key column to prevent full table scans
 * - Added composite index on userId + timestamp for efficient audit queries
 * - Proper foreign key cascade behavior
 * - Fixed table name to 'audit_entries' (matches migration)
 * - Added all missing fields required by migration and DAO
 * - Fixed column name: previousHash -> chainPrevHash
 * - UPDATED: Changed objects to enums for AuditEventType, AuditOutcome, SecurityLevel
 */
@Entity(
    tableName = "audit_entries",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"], name = "index_audit_entries_userId"),
        Index(value = ["timestamp"], name = "index_audit_entries_timestamp"),
        Index(value = ["userId", "timestamp"], name = "index_audit_entries_userId_timestamp"),
        Index(value = ["eventType"], name = "index_audit_entries_eventType"),
        Index(value = ["outcome"], name = "index_audit_entries_outcome"),
        Index(value = ["sessionId"], name = "index_audit_entries_sessionId"),
        Index(value = ["securityLevel"], name = "index_audit_entries_securityLevel"),
        Index(value = ["chainHash"], name = "index_audit_entries_chainHash")
    ]
)
data class AuditEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "userId")
    val userId: Int? = null,

    @ColumnInfo(name = "username")
    val username: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "eventType")
    val eventType: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "resourceType")
    val resourceType: String? = null,

    @ColumnInfo(name = "resourceId")
    val resourceId: String? = null,

    @ColumnInfo(name = "deviceInfo")
    val deviceInfo: String? = null,

    @ColumnInfo(name = "appVersion")
    val appVersion: String? = null,

    @ColumnInfo(name = "sessionId")
    val sessionId: String? = null,

    @ColumnInfo(name = "outcome")
    val outcome: String = "SUCCESS",

    @ColumnInfo(name = "errorMessage")
    val errorMessage: String? = null,

    @ColumnInfo(name = "securityLevel")
    val securityLevel: String = "NORMAL",

    @ColumnInfo(name = "ipAddress")
    val ipAddress: String? = null,

    @ColumnInfo(name = "userAgent")
    val userAgent: String? = null,

    @ColumnInfo(name = "location")
    val location: String? = null,

    @ColumnInfo(name = "checksum")
    val checksum: String? = null,

    @ColumnInfo(name = "chainPrevHash")
    val chainPrevHash: String? = null,

    @ColumnInfo(name = "chainHash")
    val chainHash: String? = null
)

/**
 * Audit Event Types - Enum with string values for database storage
 * UPDATED: Changed from object to enum class with 'value' property
 */
enum class AuditEventType(val value: String) {
    // Authentication Events
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT"),
    FAILED_LOGIN("FAILED_LOGIN"),

    // Item Operations
    CREATE_ITEM("CREATE_ITEM"),
    UPDATE_ITEM("UPDATE_ITEM"),
    DELETE_ITEM("DELETE_ITEM"),
    VIEW_ITEM("VIEW_ITEM"),

    // Data Operations
    EXPORT_DATA("EXPORT_DATA"),
    IMPORT_DATA("IMPORT_DATA"),

    // Security Events
    CHANGE_PASSWORD("CHANGE_PASSWORD"),
    ENABLE_BIOMETRIC("ENABLE_BIOMETRIC"),
    DISABLE_BIOMETRIC("DISABLE_BIOMETRIC"),
    DATABASE_UNLOCK("DATABASE_UNLOCK"),
    DATABASE_LOCK("DATABASE_LOCK"),

    // Backup Operations
    BACKUP_CREATED("BACKUP_CREATED"),
    BACKUP_RESTORED("BACKUP_RESTORED"),

    // System Events
    SECURITY_ALERT("SECURITY_ALERT"),
    SECURITY_EVENT("SECURITY_EVENT"),
    SYSTEM_EVENT("SYSTEM_EVENT");

    companion object {
        fun fromString(value: String): AuditEventType? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Audit Outcome Types
 * UPDATED: Changed from object to enum class with 'value' property
 */
enum class AuditOutcome(val value: String) {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    BLOCKED("BLOCKED"),
    PARTIAL("PARTIAL");

    companion object {
        fun fromString(value: String): AuditOutcome? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Security Level Types
 * UPDATED: Changed from object to enum class with 'value' property
 */
enum class SecurityLevel(val value: String) {
    CRITICAL("CRITICAL"),
    HIGH("HIGH"),
    NORMAL("NORMAL"),
    LOW("LOW"),
    INFO("INFO");

    companion object {
        fun fromString(value: String): SecurityLevel? {
            return values().find { it.value == value }
        }
    }
}