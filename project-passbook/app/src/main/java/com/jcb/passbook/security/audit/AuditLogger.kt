package com.jcb.passbook.security.audit

import android.content.Context
import android.os.Build
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditLogger provides comprehensive audit logging with tamper-evident chaining,
 * crash-safe queuing, and expanded event coverage.
 */
@Singleton
class AuditLogger @Inject constructor(
    private val auditQueue: AuditQueue,
    private val auditChainManager: AuditChainManager,
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
     * Log a user action with comprehensive audit details and tamper-evident chaining
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
                    securityLevel = securityLevel,
                    checksum = "", // Will be set by chain manager
                    chainPrevHash = "", // Will be set by chain manager
                    chainHash = "" // Will be set by chain manager
                )

                // Add to tamper-evident chain
                val chainedEntry = auditChainManager.addToChain(auditEntry)

                // Queue for crash-safe storage
                auditQueue.enqueue(chainedEntry)

                // Log to Timber for development/debugging (filtered in production)
                Timber.d("Audit: User=$username, Action=$action, Outcome=${outcome.value}, Chain=${chainedEntry.chainHash?.take(8)}")

            } catch (e: Exception) {
                // Never let audit logging crash the app, but log the failure
                Timber.e(e, "Failed to log audit entry: $action")
                logAuditFailure(userId, username, action, e.message)
            }
        }
    }

    /**
     * Log security events with elevated priority
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
     * Log authentication events with detailed context
     */
    fun logAuthentication(
        username: String,
        eventType: AuditEventType,
        outcome: AuditOutcome,
        errorMessage: String? = null,
        additionalContext: Map<String, String>? = null
    ) {
        val action = when (eventType) {
            AuditEventType.LOGIN -> "User login attempt"
            AuditEventType.LOGOUT -> "User logout"
            AuditEventType.REGISTER -> "User registration"
            AuditEventType.AUTHENTICATION_FAILURE -> "Authentication failure"
            else -> "Authentication event"
        }

        val enhancedAction = if (additionalContext != null) {
            "$action: ${additionalContext.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            action
        }

        logUserAction(
            userId = null, // Will be set after successful login
            username = username,
            eventType = eventType,
            action = enhancedAction,
            resourceType = "USER",
            resourceId = username,
            outcome = outcome,
            errorMessage = errorMessage,
            securityLevel = if (outcome == AuditOutcome.FAILURE) "ELEVATED" else "NORMAL"
        )
    }

    /**
     * Log data access events (CRUD operations) with resource details
     */
    fun logDataAccess(
        userId: Int,
        username: String?,
        eventType: AuditEventType,
        resourceType: String,
        resourceId: String,
        action: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        additionalData: Map<String, String>? = null
    ) {
        val enhancedAction = if (additionalData != null) {
            "$action: ${additionalData.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            action
        }

        logUserAction(
            userId = userId,
            username = username,
            eventType = eventType,
            action = enhancedAction,
            resourceType = resourceType,
            resourceId = resourceId,
            outcome = outcome,
            securityLevel = "NORMAL"
        )
    }

    /**
     * Log key lifecycle events
     */
    fun logKeyLifecycle(
        eventType: AuditEventType,
        keyType: String,
        keyId: String,
        action: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        errorMessage: String? = null
    ) {
        logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = eventType,
            action = "$action for $keyType",
            resourceType = "KEY",
            resourceId = keyId,
            outcome = outcome,
            errorMessage = errorMessage,
            securityLevel = "ELEVATED"
        )
    }

    /**
     * Log database lifecycle events
     */
    fun logDatabaseEvent(
        action: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        errorMessage: String? = null,
        additionalData: Map<String, String>? = null
    ) {
        val enhancedAction = if (additionalData != null) {
            "$action: ${additionalData.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            action
        }

        logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = AuditEventType.SYSTEM_EVENT,
            action = enhancedAction,
            resourceType = "DATABASE",
            resourceId = "MAIN",
            outcome = outcome,
            errorMessage = errorMessage,
            securityLevel = if (outcome == AuditOutcome.FAILURE) "CRITICAL" else "NORMAL"
        )
    }

    /**
     * Log session lifecycle events
     */
    fun logSessionEvent(
        sessionId: String,
        action: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        duration: Long? = null,
        reason: String? = null
    ) {
        val actionWithDetails = buildString {
            append(action)
            duration?.let { append(" (duration: ${it}ms)") }
            reason?.let { append(" - reason: $it") }
        }

        logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = AuditEventType.SYSTEM_EVENT,
            action = actionWithDetails,
            resourceType = "SESSION",
            resourceId = sessionId,
            outcome = outcome,
            securityLevel = "NORMAL"
        )
    }

    /**
     * Log settings and policy changes
     */
    fun logSettingsChange(
        userId: Int?,
        username: String?,
        setting: String,
        oldValue: String?,
        newValue: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS
    ) {
        val action = "Setting changed: $setting from '$oldValue' to '$newValue'"

        logUserAction(
            userId = userId,
            username = username,
            eventType = AuditEventType.UPDATE,
            action = action,
            resourceType = "SETTINGS",
            resourceId = setting,
            outcome = outcome,
            securityLevel = "ELEVATED"
        )
    }

    /**
     * Log application lifecycle events
     */
    fun logAppLifecycle(
        event: String,
        additionalData: Map<String, String>? = null
    ) {
        val action = if (additionalData != null) {
            "$event: ${additionalData.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            event
        }

        logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = AuditEventType.SYSTEM_EVENT,
            action = action,
            resourceType = "APPLICATION",
            resourceId = "LIFECYCLE",
            outcome = AuditOutcome.SUCCESS,
            securityLevel = "NORMAL"
        )
    }

    /**
     * Log audit verification results
     */
    fun logAuditVerification(
        result: String,
        entriesVerified: Int,
        discrepancies: Int = 0,
        outcome: AuditOutcome = AuditOutcome.SUCCESS
    ) {
        val action = "Audit verification: $result (verified: $entriesVerified, discrepancies: $discrepancies)"

        logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = AuditEventType.SYSTEM_EVENT,
            action = action,
            resourceType = "AUDIT",
            resourceId = "VERIFICATION",
            outcome = outcome,
            securityLevel = if (discrepancies > 0) "CRITICAL" else "NORMAL"
        )
    }

    /**
     * Log audit failure (used internally when audit logging itself fails)
     */
    private fun logAuditFailure(
        userId: Int?,
        username: String?,
        originalAction: String,
        errorMessage: String?
    ) {
        auditScope.launch {
            try {
                val failureEntry = AuditEntry(
                    userId = userId,
                    username = username,
                    timestamp = System.currentTimeMillis(),
                    eventType = AuditEventType.SYSTEM_EVENT.value,
                    action = "AUDIT_LOG_FAILURE: Failed to log '$originalAction'",
                    deviceInfo = deviceInfo,
                    appVersion = appVersion,
                    sessionId = sessionId,
                    outcome = AuditOutcome.FAILURE.value,
                    errorMessage = "Audit logging failed: $errorMessage",
                    securityLevel = "CRITICAL",
                    checksum = "",
                    chainPrevHash = "",
                    chainHash = ""
                )

                // Try to add to chain without causing recursion
                val chainedEntry = try {
                    auditChainManager.addToChain(failureEntry)
                } catch (e: Exception) {
                    failureEntry.copy(checksum = failureEntry.generateChecksum())
                }

                auditQueue.enqueue(chainedEntry)
            } catch (innerE: Exception) {
                Timber.e(innerE, "Critical: Failed to log audit failure - audit system may be compromised")
            }
        }
    }

    /**
     * Get current session ID for correlation
     */
    fun getCurrentSessionId(): String = sessionId

    /**
     * Force flush of pending audit entries
     */
    suspend fun flush() {
        auditQueue.flush()
    }
}