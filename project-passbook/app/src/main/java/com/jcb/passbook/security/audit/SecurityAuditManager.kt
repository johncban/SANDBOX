package com.jcb.passbook.security.audit

import android.content.Context
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.detection.SecurityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SecurityAuditManager - Monitors security events and detects anomalies
 *
 * ✅ FIXED: Uses lazy AuditLogger injection to break circular dependency
 * ✅ REFACTORED: Uses public SecurityManager.checkRootStatus() instead of private isDeviceCompromised()
 */
@Singleton
class SecurityAuditManager @Inject constructor(
    private val auditLoggerProvider: () -> AuditLogger,  // ✅ Lazy provider
    private val auditDao: AuditDao,
    @ApplicationContext private val context: Context
) {
    // ✅ Lazy initialization of AuditLogger
    private val auditLogger: AuditLogger by lazy { auditLoggerProvider() }

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
    private val activeSessions = mutableMapOf<String, Long>()

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
                    Timber.e(e, "Error in security monitoring")
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

    /**
     * ✅ FIXED: Uses SecurityManager.checkRootStatus() which is public
     * and SecurityManager.isCompromised state
     */
    private suspend fun performSecurityChecks() {
        // ✅ FIXED: Use checkRootStatus callback to detect compromise
        var isCompromised = false

        withContext(Dispatchers.Default) {
            SecurityManager.checkRootStatus(context) {
                // This callback is invoked if device is compromised
                isCompromised = true
            }
        }

        if (isCompromised) {
            auditLogger.logSecurityEvent(
                "Device security compromise detected (root/emulator/debugger)",
                "CRITICAL",
                AuditOutcome.WARNING
            )
            addSecurityAlert(
                "CRITICAL",
                "Device security compromised",
                "App access blocked for security"
            )
        }

        // Check for compromised state flag
        if (SecurityManager.isCompromised.value) {
            auditLogger.logSecurityEvent(
                "Active security compromise state detected",
                "CRITICAL",
                AuditOutcome.WARNING
            )
            addSecurityAlert(
                "CRITICAL",
                "Security compromise active",
                "Session terminated"
            )
        }

        // Anomaly detection logic
        checkForAnomalousActivity()
    }

    // ============================================================================================
    // ANOMALY DETECTION
    // ============================================================================================

    /**
     * Main anomaly detection entry point
     */
    private suspend fun checkForAnomalousActivity() {
        detectLoginAnomalies()
        detectCrudSpikeAnomalies()
        detectSessionAnomalies()
        detectUnusualAppUsageTimes()
    }

    /**
     * 1. Detect excessive authentication failures
     */
    private suspend fun detectLoginAnomalies() {
        val since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        val failedLogins = auditDao.countAllEventsSince(since)

        if (failedLogins > 10) {
            auditLogger.logSecurityEvent(
                "High volume of authentication failures ($failedLogins in last hour)",
                "ELEVATED",
                AuditOutcome.WARNING
            )
            addSecurityAlert(
                "ELEVATED",
                "Excessive authentication failures",
                "Account lockout recommended"
            )
        }
    }

    /**
     * 2. Detect spikes in CRUD operations
     */
    private suspend fun detectCrudSpikeAnomalies() {
        val since = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)
        val recentCreates = auditDao.countAllEventsSince(since)
        val recentReads = auditDao.countAllEventsSince(since)

        if (recentCreates > 20 || recentReads > 50) {
            auditLogger.logSecurityEvent(
                "Possible brute-force or automated activity detected (CRUD ops)",
                "ELEVATED",
                AuditOutcome.WARNING
            )
            addSecurityAlert(
                "ELEVATED",
                "Unusual CRUD activity",
                "Review user activity logs"
            )
        }
    }

    /**
     * 3. Detect abnormal session durations
     */
    private suspend fun detectSessionAnomalies() {
        val suspiciousSessions = auditDao.getAuditEntriesByType(
            AuditEventType.SYSTEM_EVENT
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
            addSecurityAlert(
                "WARNING",
                "Session anomaly detected",
                "Session forcibly terminated"
            )
        }
    }

    /**
     * 4. Detect usage at unexpected times
     */
    private suspend fun detectUnusualAppUsageTimes() {
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val entries = auditDao.getAuditEntriesInTimeRange(
            startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
            endTime = System.currentTimeMillis()
        ).first()

        val nightLogins = entries.count {
            it.eventType == AuditEventType.LOGIN && (nowHour < 6 || nowHour > 22)
        }

        if (nightLogins > 3) {
            auditLogger.logSecurityEvent(
                "Unusual app usage hours detected ($nightLogins logins at late hours)",
                "WARNING",
                AuditOutcome.WARNING
            )
            addSecurityAlert(
                "WARNING",
                "Unusual access hours",
                "Flag for review"
            )
        }
    }

    // ============================================================================================
    // ALERT MANAGEMENT
    // ============================================================================================

    private fun addSecurityAlert(severity: String, message: String, action: String) {
        val alert = SecurityAlert(
            timestamp = System.currentTimeMillis(),
            severity = severity,
            message = message,
            action = action
        )

        val currentAlerts = _securityAlerts.value.toMutableList()
        currentAlerts.add(0, alert)

        // Keep only last 50 alerts
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

    /**
     * Clear all security alerts
     */
    fun clearAlerts() {
        _securityAlerts.value = emptyList()
        auditLogger.logSecurityEvent(
            "Security alerts cleared",
            "NORMAL",
            AuditOutcome.SUCCESS
        )
    }

    /**
     * Get alerts by severity
     */
    fun getAlertsBySeverity(severity: String): List<SecurityAlert> {
        return _securityAlerts.value.filter { it.severity == severity }
    }
}
