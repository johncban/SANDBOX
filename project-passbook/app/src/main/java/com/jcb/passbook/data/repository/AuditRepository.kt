package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepository @Inject constructor(
    private val auditDao: AuditDao
) {

    // ✅ FIX: Remove second parameter, use AuditEventType enum
    fun getAuditEntriesForUser(userId: Long): Flow<List<AuditEntry>> {
        return auditDao.getAuditEntriesForUser(userId)  // Removed second param
    }

    // ✅ FIX: Convert String to AuditEventType enum
    fun getAuditEntriesByType(eventType: AuditEventType): Flow<List<AuditEntry>> {
        return auditDao.getAuditEntriesByType(eventType)  // Use enum directly
    }

    // ✅ FIX: Remove parameter
    fun getFailedAuditEntries(): Flow<List<AuditEntry>> {
        return auditDao.getFailedAuditEntries()  // No parameters
    }

    // ✅ FIX: Remove parameter
    fun getCriticalSecurityEvents(): Flow<List<AuditEntry>> {
        return auditDao.getCriticalSecurityEvents()  // No parameters
    }

    suspend fun getRecentFailedLogins(username: String, withinHours: Int = 24): Int {
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(withinHours.toLong())
        // This would need a custom query to filter by username
        return 0 // Simplified for now
    }

    suspend fun cleanupOldAuditEntries(retentionYears: Int = 6): Int {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365L * retentionYears)
        return auditDao.deleteOldEntries(cutoffTime)
    }

    suspend fun verifyAuditIntegrity(): Boolean {
        val entriesWithoutChecksum = auditDao.countEntriesWithoutChecksum()
        return entriesWithoutChecksum == 0
    }
}