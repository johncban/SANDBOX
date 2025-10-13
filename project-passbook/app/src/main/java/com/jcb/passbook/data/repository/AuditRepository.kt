package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepository @Inject constructor(
    private val auditDao: AuditDao
) {

    fun getAuditEntriesForUser(userId: Int, limit: Int = 1000): Flow<List<AuditEntry>> =
        auditDao.getAuditEntriesForUser(userId, limit)

    fun getAuditEntriesByType(eventType: String, limit: Int = 1000): Flow<List<AuditEntry>> =
        auditDao.getAuditEntriesByType(eventType, limit)

    fun getFailedAuditEntries(limit: Int = 500): Flow<List<AuditEntry>> =
        auditDao.getFailedAuditEntries(limit)

    fun getCriticalSecurityEvents(limit: Int = 100): Flow<List<AuditEntry>> =
        auditDao.getCriticalSecurityEvents(limit)

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