package com.jcb.passbook.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing audit log entries in the database.
 * Tracks all security-related events and user activities.
 */
@Entity(
    tableName = "audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["event_type"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "log_id")
    val logId: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "success")
    val success: Boolean,

    @ColumnInfo(name = "details")
    val details: String? = null,

    @ColumnInfo(name = "ip_address")
    val ipAddress: String? = null,

    @ColumnInfo(name = "device_info")
    val deviceInfo: String? = null
)

/**
 * Enum representing different types of audit events.
 */
enum class AuditEventType {
    SYSTEM_START,
    USER_LOGIN,
    USER_LOGOUT,
    USER_REGISTER,
    ITEM_CREATE,
    ITEM_UPDATE,
    ITEM_DELETE,
    ITEM_VIEW,
    PASSWORD_CHANGE,
    SECURITY_CHECK,
    DATABASE_ACCESS,
    ENCRYPTION_OPERATION,
    FAILED_LOGIN,
    SESSION_EXPIRED
}

/**
 * Enum representing the outcome of audit events.
 */
enum class AuditOutcome {
    SUCCESS,
    FAILURE,
    WARNING,
    INFO
}
