package com.jcb.passbook.util.audit


import android.content.Context
import android.os.Build
import com.jcb.passbook.room.AuditDao
import com.jcb.passbook.room.AuditEntry
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val auditDao: AuditDao,
    @ApplicationContext private val context: Context
) {
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionId = UUID.randomUUID().toString()

    // Device information for audit context
    private val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    private val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "Unknown"
    }

    /**
     * Log a user action with comprehensive audit details
     */
    fun logUserAction(
        userId: Int?,
        username: String?,
        eventType: AuditEventType,
        action: String,
        resourceType: String? = null,
        resourceId: String? = null,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        errorMessage: String? = null,
        securityLevel: String = "NORMAL"
    ) {
        auditScope.launch {
            try {
                val auditEntry = AuditEntry(
                    userId = userId,
                    username = username,
                    timestamp = System.currentTimeMillis(),
                    eventType = eventType.value,
                    action = action,
                    resourceType = resourceType,
                    resourceId = resourceId,
                    deviceInfo = deviceInfo,
                    appVersion = appVersion,
                    sessionId = sessionId,
                    outcome = outcome.value,
                    errorMessage = errorMessage,
                    securityLevel = securityLevel
                )

                // Generate integrity checksum
                val checksum = auditEntry.generateChecksum()
                val auditWithChecksum = auditEntry.copy(checksum = checksum)

                // Insert audit entry
                val auditId = auditDao.insert(auditWithChecksum)

                // Log to Timber for development/debugging (will be filtered in production)
                Timber.d("Audit: [$auditId] User=$username, Action=$action, Outcome=${outcome.value}")

            } catch (e: Exception) {
                // Never let audit logging crash the app, but log the failure
                Timber.e(e, "Failed to log audit entry: $action")

                // Try to log the audit failure itself
                try {
                    val failureEntry = AuditEntry(
                        userId = userId,
                        username = username,
                        timestamp = System.currentTimeMillis(),
                        eventType = AuditEventType.SYSTEM_EVENT.value,
                        action = "AUDIT_LOG_FAILURE",
                        deviceInfo = deviceInfo,
                        appVersion = appVersion,
                        sessionId = sessionId,
                        outcome = AuditOutcome.FAILURE.value,
                        errorMessage = "Audit logging failed: ${e.message}",
                        securityLevel = "CRITICAL"
                    )
                    auditDao.insert(failureEntry.copy(checksum = failureEntry.generateChecksum()))
                } catch (innerE: Exception) {
                    Timber.e(innerE, "Critical: Failed to log audit failure")
                }
            }
        }
    }

    /**
     * Log security events (root detection, tampering, etc.)
     */
    fun logSecurityEvent(
        eventDescription: String,
        severity: String = "CRITICAL",
        outcome: AuditOutcome = AuditOutcome.WARNING
    ) {
        logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = AuditEventType.SECURITY_EVENT,
            action = eventDescription,
            resourceType = "SYSTEM",
            resourceId = "DEVICE",
            outcome = outcome,
            securityLevel = severity
        )
    }

    /**
     * Log authentication events
     */
    fun logAuthentication(
        username: String,
        eventType: AuditEventType,
        outcome: AuditOutcome,
        errorMessage: String? = null
    ) {
        logUserAction(
            userId = null, // Will be set after successful login
            username = username,
            eventType = eventType,
            action = when (eventType) {
                AuditEventType.LOGIN -> "User login attempt"
                AuditEventType.LOGOUT -> "User logout"
                AuditEventType.REGISTER -> "User registration"
                AuditEventType.AUTHENTICATION_FAILURE -> "Authentication failure"
                else -> "Authentication event"
            },
            resourceType = "USER",
            resourceId = username,
            outcome = outcome,
            errorMessage = errorMessage,
            securityLevel = if (outcome == AuditOutcome.FAILURE) "ELEVATED" else "NORMAL"
        )
    }

    /**
     * Log data access events (CRUD operations)
     */
    fun logDataAccess(
        userId: Int,
        username: String?,
        eventType: AuditEventType,
        resourceType: String,
        resourceId: String,
        action: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS
    ) {
        logUserAction(
            userId = userId,
            username = username,
            eventType = eventType,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            outcome = outcome,
            securityLevel = "NORMAL"
        )
    }
}