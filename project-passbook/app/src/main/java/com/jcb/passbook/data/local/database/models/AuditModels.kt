package com.jcb.passbook.data.local.database.models

/**
 * AuditModels - Data models for AuditDao query results
 *
 * IMPORTANT: These are NOT Room entities!
 * They are simple data classes used for mapping query results.
 *
 * CRITICAL FIX: Moved from AuditDao.kt to separate file
 * - Data classes in DAO files confuse KAPT annotation processor
 * - Causes NullPointerException during Room code generation
 * - Room @Dao files should ONLY contain interface and extensions
 *
 * These classes are used by AuditDao statistics and maintenance queries.
 */

/**
 * Event type statistics result
 * Used by: AuditDao.getEventTypeStatistics()
 */
data class EventTypeCount(
    val eventType: String,
    val count: Int
)

/**
 * Outcome statistics result
 * Used by: AuditDao.getOutcomeStatistics()
 */
data class OutcomeCount(
    val outcome: String,
    val count: Int
)

/**
 * Security level statistics result
 * Used by: AuditDao.getSecurityLevelStatistics()
 */
data class SecurityLevelCount(
    val securityLevel: String,
    val count: Int
)

/**
 * Checksum information result
 * Used by: AuditDao.getAllChecksums()
 */
data class ChecksumInfo(
    val id: Long,
    val checksum: String?
)

/**
 * Chain hash information result
 * Used by: AuditDao.getAllChainHashes()
 */
data class ChainInfo(
    val id: Long,
    val chainPrevHash: String?,
    val chainHash: String?
)