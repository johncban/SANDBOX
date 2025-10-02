package com.jcb.passbook.util.audit

import android.content.Context
import com.jcb.passbook.room.AuditDao
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import com.jcb.passbook.util.security.RootDetector
import com.jcb.passbook.util.security.SecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit


@Singleton
class SecurityAuditManager @Inject constructor(
    private val auditLogger: AuditLogger,
    private val auditDao: AuditDao, // Inject your Room AuditDao
    @ApplicationContext private val context: Context
) {
    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _securityAlerts = MutableStateFlow<List<SecurityAlert>>(emptyList())
    val securityAlerts: StateFlow<List<SecurityAlert>> = _securityAlerts.asStateFlow()

    data class SecurityAlert(
        val timestamp: Long,
        val severity: String,
        val message: String,
        val action: String
    )

    // Optional: Session cache (could use a persistent store)
    private val activeSessions = mutableMapOf<String, Long>() // sessionId -> lastActivityTime

    fun startSecurityMonitoring() {
        monitoringScope.launch {
            while (isActive) {
                performSecurityChecks()
                delay(5 * 60 * 1000) // Check every 5 minutes
            }
        }
    }

    private suspend fun performSecurityChecks() {
        // Root detection
        if (RootDetector.isDeviceRooted(context)) {
            auditLogger.logSecurityEvent(
                "Root access detected on device",
                "CRITICAL",
                AuditOutcome.WARNING
            )
            addSecurityAlert("CRITICAL", "Device is rooted", "App terminated for security")
        }

        // Security compromise check
        if (SecurityManager.isCompromised.value) {
            auditLogger.logSecurityEvent(
                "Device security compromise detected",
                "CRITICAL",
                AuditOutcome.BLOCKED
            )
            addSecurityAlert("CRITICAL", "Security compromise", "App access blocked")
        }

        // Anomaly detection logic
        checkForAnomalousActivity()
    }

    // Main anomaly detection entry point
    private suspend fun checkForAnomalousActivity() {
        detectLoginAnomalies()
        detectCrudSpikeAnomalies()
        detectSessionAnomalies()
        detectUnusualAppUsageTimes()
    }

    // 1. Detect excessive authentication failures
    private suspend fun detectLoginAnomalies() {
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        val failedLogins = auditDao.countEventsSince(
            userId = null, // Optionally loop per user
            eventType = AuditEventType.AUTHENTICATION_FAILURE.value,
            since = since
        )
        if (failedLogins > 10) { // Threshold, adjust as needed
            auditLogger.logSecurityEvent(
                "High volume of authentication failures ($failedLogins in last hour)",
                "ELEVATED", AuditOutcome.WARNING
            )
            addSecurityAlert("ELEVATED", "Excessive authentication failures", "Account lockout recommended")
        }
    }

    // 2. Detect spikes in CRUD operations (brute force or automation)
    private suspend fun detectCrudSpikeAnomalies() {
        val since = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)
        val recentCreates = auditDao.countEventsSince(
            userId = null,
            eventType = AuditEventType.CREATE.value,
            since = since
        )
        val recentReads = auditDao.countEventsSince(
            userId = null,
            eventType = AuditEventType.READ.value,
            since = since
        )
        if (recentCreates > 20 || recentReads > 50) {
            auditLogger.logSecurityEvent(
                "Possible brute-force or automated activity detected (CRUD ops)",
                "ELEVATED", AuditOutcome.WARNING
            )
            addSecurityAlert("ELEVATED", "Unusual CRUD activity", "Review user activity logs")
        }
    }

    // 3. Detect abnormal session durations (very long or very short)
    private suspend fun detectSessionAnomalies() {
        val lastHour = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        // Query audit entries for session events, analyze durations
        val suspiciousSessions = auditDao.getAuditEntriesByType(
            eventType = AuditEventType.SYSTEM_EVENT.value,
            limit = 100
        ).first().filter {
            // Suppose AuditEntry has sessionId, timestamp fields
            val duration = it.timestamp - (activeSessions[it.sessionId] ?: it.timestamp)
            duration > TimeUnit.HOURS.toMillis(12) || duration < TimeUnit.SECONDS.toMillis(10)
        }
        if (suspiciousSessions.isNotEmpty()) {
            auditLogger.logSecurityEvent(
                "Abnormal session duration detected",
                "WARNING", AuditOutcome.WARNING
            )
            addSecurityAlert("WARNING", "Session anomaly detected", "Session forcibly terminated")
        }
    }

    // 4. Detect usage at unexpected times (late night, abnormal times)
    private suspend fun detectUnusualAppUsageTimes() {
        val nowHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val entries = auditDao.getAuditEntriesInTimeRange(
            startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
            endTime = System.currentTimeMillis()
        ).first()
        val nightLogins = entries.count {
            it.eventType == AuditEventType.LOGIN.value && (nowHour < 6 || nowHour > 22)
        }
        if (nightLogins > 3) { // Threshold for abnormal usage
            auditLogger.logSecurityEvent(
                "Unusual app usage hours detected ($nightLogins logins at late hours)",
                "WARNING", AuditOutcome.WARNING
            )
            addSecurityAlert("WARNING", "Unusual access hours", "Flag for review")
        }
    }

    private fun addSecurityAlert(severity: String, message: String, action: String) {
        val alert = SecurityAlert(
            timestamp = System.currentTimeMillis(),
            severity = severity,
            message = message,
            action = action
        )

        val currentAlerts = _securityAlerts.value.toMutableList()
        currentAlerts.add(0, alert)
        if (currentAlerts.size > 50) {
            currentAlerts.removeAt(currentAlerts.size - 1)
        }
        _securityAlerts.value = currentAlerts
    }
}
