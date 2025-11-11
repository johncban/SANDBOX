package com.jcb.passbook.data.local.database.models

import androidx.room.ColumnInfo

/**
 * Data class for event type statistics
 */
data class EventTypeCount(
    @ColumnInfo(name = "eventType")  // ✅ FIXED: Must match query alias
    val eventType: String,
    val count: Int
)

/**
 * Data class for outcome statistics
 */
data class OutcomeCount(
    val outcome: String,
    val count: Int
)

/**
 * Data class for security level statistics
 */
data class SecurityLevelCount(
    @ColumnInfo(name = "securityLevel")  // ✅ FIXED: Must match query alias
    val securityLevel: String,
    val count: Int
)

/**
 * Data class for checksum validation
 */
data class ChecksumInfo(
    val id: Long,
    val checksum: String
)

/**
 * Data class for audit chain information
 */
data class ChainInfo(
    val id: Long,
    @ColumnInfo(name = "chainHash")      // ✅ FIXED: Must match query alias
    val chainHash: String?,
    @ColumnInfo(name = "chainPrevHash")  // ✅ FIXED: Must match query alias
    val chainPrevHash: String?
)
