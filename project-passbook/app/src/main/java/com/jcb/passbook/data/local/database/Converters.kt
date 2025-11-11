package com.jcb.passbook.data.local.database

import androidx.room.TypeConverter
import com.jcb.passbook.data.local.database.entities.AuditEventType
import java.util.Date

/**
 * Type converters for Room database
 *
 * Handles conversion between Kotlin types and SQLite-compatible types
 */
class Converters {

    // ==================== AuditEventType Converters ====================

    /**
     * Convert AuditEventType enum to String for database storage
     */
    @TypeConverter
    fun fromAuditEventType(value: AuditEventType): String {
        return value.name
    }

    /**
     * Convert String from database to AuditEventType enum
     */
    @TypeConverter
    fun toAuditEventType(value: String): AuditEventType {
        return try {
            AuditEventType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Default fallback if enum value not found
            AuditEventType.SYSTEM_START
        }
    }

    // ==================== Date Converters ====================

    /**
     * Convert Date to Long timestamp for database storage
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    /**
     * Convert Long timestamp from database to Date
     */
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }

    // ==================== List<String> Converters ====================

    /**
     * Convert List<String> to comma-separated String for database storage
     */
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(separator = ",")
    }

    /**
     * Convert comma-separated String from database to List<String>
     */
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
    }

    // ==================== ByteArray Converters ====================

    /**
     * Convert ByteArray to Base64 String for database storage
     */
    @TypeConverter
    fun fromByteArray(bytes: ByteArray?): String? {
        return bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) }
    }

    /**
     * Convert Base64 String from database to ByteArray
     */
    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
    }
}
