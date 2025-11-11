package com.jcb.passbook.data.local.database.dao

import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import kotlinx.coroutines.flow.Flow

/**
 * Extension functions for AuditDao to provide default parameter values
 * Separated from interface to avoid compilation issues
 */

// Default limit: 1000 entries
fun AuditDao.getAuditEntriesForUser(userId: Long): Flow<List<AuditEntry>> =
    getAuditEntriesForUser(userId, 1000)

fun AuditDao.getAuditEntriesByType(eventType: AuditEventType): Flow<List<AuditEntry>> =
    getAuditEntriesByType(eventType, 1000)

// Default limit: 500 for failed entries
fun AuditDao.getFailedAuditEntries(): Flow<List<AuditEntry>> =
    getFailedAuditEntries(500)

// Default limit: 100 for critical events
fun AuditDao.getCriticalSecurityEvents(): Flow<List<AuditEntry>> =
    getCriticalSecurityEvents(100)

// Default limit: 1000 for all entries
fun AuditDao.getAllAuditEntries(): Flow<List<AuditEntry>> =
    getAllAuditEntries(1000)

// Advanced search with default parameters
fun AuditDao.searchAuditEntries(
    userId: Long? = null,
    eventType: String? = null,
    outcome: String? = null,
    securityLevel: String? = null,
    startTime: Long,
    endTime: Long
): Flow<List<AuditEntry>> =
    searchAuditEntries(userId, eventType, outcome, securityLevel, startTime, endTime, 1000)

// Time range helpers
fun AuditDao.getEntriesLastHour(): Flow<List<AuditEntry>> {
    val now = System.currentTimeMillis()
    val oneHourAgo = now - (60 * 60 * 1000)
    return getAuditEntriesInTimeRange(oneHourAgo, now)
}

fun AuditDao.getEntriesToday(): Flow<List<AuditEntry>> {
    val now = System.currentTimeMillis()
    val startOfDay = now - (now % (24 * 60 * 60 * 1000))
    return getAuditEntriesInTimeRange(startOfDay, now)
}

fun AuditDao.getEntriesLastWeek(): Flow<List<AuditEntry>> {
    val now = System.currentTimeMillis()
    val oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000)
    return getAuditEntriesInTimeRange(oneWeekAgo, now)
}

// Bulk operations
suspend fun AuditDao.insertMultiple(entries: List<AuditEntry>) {
    entries.forEach { insertOrUpdate(it) }
}

// Cleanup helper
suspend fun AuditDao.deleteEntriesOlderThanDays(days: Int): Int {
    val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
    return deleteOldEntries(cutoffTime)
}
