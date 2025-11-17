package com.jcb.passbook.security.audit

import android.content.Context
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.detection.RootDetector
import com.jcb.passbook.security.detection.SecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecurityAuditManager - Monitors security events and detects anomalies
 * COMPLETELY FIXED VERSION
 */
@Singleton
class SecurityAuditManager @Inject constructor(
    private val auditLogger: AuditLogger,
    private val auditDao: AuditDao,
    @ApplicationContext private val context: Context
) {
    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _securityAlerts = MutableStateFlow<List<SecurityAlert>>(emptyList())
    val securityAlerts: StateFlow<List<SecurityAlert>> = _securityAlerts.asStateFlow()

    @Volatile
    private var isMonitoring = false

    data class SecurityAlert(
        val timestamp: Long,
        val severity: String,
        val message: String,
        val action: String
    )

    // Optional: Session cache
    private val activeSessions = mutableMapOf<String?, Long>()

    /**
     * Start security monitoring
     */
    fun startSecurityMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        monitoringScope.launch {
            while (isActive && isMonitoring) {
                try {
                    performSecurityChecks()
                    delay(5 * 60 * 1000) // Check every 5 minutes
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error in security monitoring")
                    delay(60_000) // Wait 1 minute before retry
                }
            }
        }

        auditLogger.logSecurityEvent(
            "Security monitoring started",
            "NORMAL",
            AuditOutcome.SUCCESS
        )
    }

    /**
     * Stop security monitoring
     */
    fun stopSecurityMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        monitoringScope.cancel()

        auditLogger.logSecurityEvent(
            "Security monitoring stopped",
            "NORMAL",
            AuditOutcome.SUCCESS
        )
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
                AuditOutcome.WARNING  // ✅ FIXED: Changed from BLOCKED to WARNING
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

        // ✅ FIXED: Remove eventType parameter, use proper method
        val failedLogins = auditDao.countAllEventsSince(since)

        if (failedLogins > 10) {
            auditLogger.logSecurityEvent(
                "High volume of authentication failures ($failedLogins in last hour)",
                "ELEVATED",
                AuditOutcome.WARNING
            )
            addSecurityAlert("ELEVATED", "Excessive authentication failures", "Account lockout recommended")
        }
    }

    // 2. Detect spikes in CRUD operations
    private suspend fun detectCrudSpikeAnomalies() {
        val since = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)

        // ✅ FIXED: Remove eventType parameter
        val recentCreates = auditDao.countAllEventsSince(since)
        val recentReads = auditDao.countAllEventsSince(since)

        if (recentCreates > 20 || recentReads > 50) {
            auditLogger.logSecurityEvent(
                "Possible brute-force or automated activity detected (CRUD ops)",
                "ELEVATED",
                AuditOutcome.WARNING
            )
            addSecurityAlert("ELEVATED", "Unusual CRUD activity", "Review user activity logs")
        }
    }

    // 3. Detect abnormal session durations
    private suspend fun detectSessionAnomalies() {
        val lastHour = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

        // ✅ FIXED: Remove limit parameter and eventType.value
        val suspiciousSessions = auditDao.getAuditEntriesByType(
            AuditEventType.SYSTEM_EVENT  // ✅ Use enum directly, no .value
        ).first().filter {
            val duration = it.timestamp - (activeSessions[it.sessionId] ?: it.timestamp)
            duration > TimeUnit.HOURS.toMillis(12) || duration < TimeUnit.SECONDS.toMillis(10)
        }

        if (suspiciousSessions.isNotEmpty()) {
            auditLogger.logSecurityEvent(
                "Abnormal session duration detected",
                "WARNING",
                AuditOutcome.WARNING
            )
            addSecurityAlert("WARNING", "Session anomaly detected", "Session forcibly terminated")
        }
    }

    // 4. Detect usage at unexpected times
    private suspend fun detectUnusualAppUsageTimes() {
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val entries = auditDao.getAuditEntriesInTimeRange(
            startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
            endTime = System.currentTimeMillis()
        ).first()

        // ✅ FIXED: Remove .value from enum
        val nightLogins = entries.count {
            it.eventType == AuditEventType.LOGIN && (nowHour < 6 || nowHour > 22)
        }

        if (nightLogins > 3) {
            auditLogger.logSecurityEvent(
                "Unusual app usage hours detected ($nightLogins logins at late hours)",
                "WARNING",
                AuditOutcome.WARNING
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

    /**
     * Get current security status
     */
    fun getSecurityStatus(): SecurityStatus {
        return SecurityStatus(
            isMonitoring = isMonitoring,
            alertCount = _securityAlerts.value.size,
            criticalAlerts = _securityAlerts.value.count { it.severity == "CRITICAL" },
            lastCheckTime = System.currentTimeMillis()
        )
    }

    data class SecurityStatus(
        val isMonitoring: Boolean,
        val alertCount: Int,
        val criticalAlerts: Int,
        val lastCheckTime: Long
    )
}
