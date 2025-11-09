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
 */
@Entity(
    tableName = "audit_entries",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,  // Delete audit logs when user is deleted
            onUpdate = ForeignKey.CASCADE    // Update audit logs when user ID changes
        )
    ],
    indices = [
        Index(value = ["userId"], name = "index_audit_entries_userId"),  // CRITICAL FIX: Prevents full table scans
        Index(value = ["timestamp"], name = "index_audit_entries_timestamp"),  // Performance: Sort by time
        Index(value = ["userId", "timestamp"], name = "index_audit_entries_userId_timestamp"),  // Composite index for user audit history
        Index(value = ["action"], name = "index_audit_entries_action")  // Performance: Filter by action type
    ]
)
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Int,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    // Add missing fields required by AuditDao
    val checksum: String? = null,
    val previousHash: String? = null,
    val chainHash: String? = null,
    val sessionId: String? = null,
    val securityLevel: String = "INFO",
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val location: String? = null
)

/**
 * Audit Action Types - Enum for type-safe action tracking
 */
enum class AuditAction(val action: String) {
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT"),
    FAILED_LOGIN("FAILED_LOGIN"),
    CREATE_ITEM("CREATE_ITEM"),
    UPDATE_ITEM("UPDATE_ITEM"),
    DELETE_ITEM("DELETE_ITEM"),
    VIEW_ITEM("VIEW_ITEM"),
    EXPORT_DATA("EXPORT_DATA"),
    IMPORT_DATA("IMPORT_DATA"),
    CHANGE_PASSWORD("CHANGE_PASSWORD"),
    ENABLE_BIOMETRIC("ENABLE_BIOMETRIC"),
    DISABLE_BIOMETRIC("DISABLE_BIOMETRIC")
}