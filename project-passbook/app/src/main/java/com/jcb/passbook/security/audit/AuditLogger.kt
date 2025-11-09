package com.jcb.passbook.security.audit

import android.content.Context
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
 * AuditLogger - Centralized audit logging for security tracking
 *
 * FIXES APPLIED:
 * - All enum references now use proper AuditEventType, AuditOutcome enums
 * - Removed invalid event types (CREATE, UPDATE, DELETE, READ)
 * - Added proper event type mapping for CRUD operations
 * - Fixed all .value property access
 * - Removed string literal usage for enums
 * - Added missing SECURITY_EVENT and SYSTEM_EVENT to enum
 * - Fixed all Kotlin string templates ($variable syntax)
 */
@Singleton
class AuditLogger @Inject constructor(
    private val auditQueue: AuditQueue,
    private val auditChainManager: AuditChainManager,
    @ApplicationContext private val context: Context
) {
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Log user action with optional parameters for easier calling
     */
    fun logUserAction(
        userId: Int?,
        username: String,
        eventType: AuditEventType,
        action: String,
        resourceType: String?,
        resourceId: String?,
        outcome: AuditOutcome,
        errorMessage: String? = null,
        securityLevel: String = "NORMAL"
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = userId,
                    username = username,
                    eventType = eventType,
                    action = action,
                    resourceType = resourceType,
                    resourceId = resourceId,
                    outcome = outcome,
                    errorMessage = errorMessage,
                    securityLevel = securityLevel
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log user action")
            }
        }
    }

    /**
     * Log security event with optional error message
     */
    fun logSecurityEvent(
        message: String,
        securityLevel: String,
        outcome: AuditOutcome,
        errorMessage: String? = null
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.SECURITY_EVENT,
                    action = message,
                    resourceType = "SECURITY",
                    resourceId = "EVENT",
                    outcome = outcome,
                    errorMessage = errorMessage,
                    securityLevel = securityLevel
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log security event")
            }
        }
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
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = null,
                    username = username,
                    eventType = eventType,
                    action = "Authentication attempt",
                    resourceType = "AUTH",
                    resourceId = "BIOMETRIC",
                    outcome = outcome,
                    errorMessage = errorMessage,
                    securityLevel = "ELEVATED"
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log authentication")
            }
        }
    }

    /**
     * Log audit verification results
     */
    fun logAuditVerification(
        result: String,
        entriesVerified: Int,
        discrepancies: Int,
        outcome: AuditOutcome
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.SYSTEM_EVENT,
                    action = "Audit verification: $result (verified: $entriesVerified, discrepancies: $discrepancies)",
                    resourceType = "AUDIT",
                    resourceId = "VERIFICATION",
                    outcome = outcome,
                    errorMessage = if (discrepancies > 0) "$discrepancies discrepancies found" else null,
                    securityLevel = if (discrepancies > 0) "CRITICAL" else "NORMAL"
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log audit verification")
            }
        }
    }

    /**
     * Log application lifecycle events
     */
    fun logAppLifecycle(
        action: String,
        metadata: Map<String, Any>
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.SYSTEM_EVENT,
                    action = "$action - ${metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}",
                    resourceType = "SYSTEM",
                    resourceId = "LIFECYCLE",
                    outcome = AuditOutcome.SUCCESS,
                    errorMessage = null,
                    securityLevel = "NORMAL"
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log app lifecycle")
            }
        }
    }

    /**
     * Log database operations with optional parameters
     */
    fun logDatabaseOperation(
        operation: String,
        tableName: String?,
        recordId: String?,
        outcome: AuditOutcome,
        errorMessage: String? = null
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.SYSTEM_EVENT,
                    action = "Database operation: $operation",
                    resourceType = "DATABASE",
                    resourceId = tableName ?: "UNKNOWN",
                    outcome = outcome,
                    errorMessage = errorMessage,
                    securityLevel = "NORMAL"
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log database operation")
            }
        }
    }

    /**
     * Log item operations (create/read/update/delete)
     */
    fun logItemOperation(
        userId: Int,
        operation: AuditEventType,
        itemId: String?,
        outcome: AuditOutcome,
        errorMessage: String? = null
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = userId,
                    username = "USER_$userId",
                    eventType = operation,
                    action = "Item operation: ${operation.value}",
                    resourceType = "ITEM",
                    resourceId = itemId,
                    outcome = outcome,
                    errorMessage = errorMessage,
                    securityLevel = "NORMAL"
                )

                val chainedEntry = auditChainManager.addToChain(auditEntry)
                auditQueue.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log item operation")
            }
        }
    }

    /**
     * FIXED: Log data access events with correct enum usage
     * Called from ItemViewModel when accessing password vault items
     */
    fun logDataAccess(
        userId: Int?,
        username: String,
        action: String,
        resourceType: String?,
        resourceId: String?,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        errorMessage: String? = null,
        securityLevel: String = "NORMAL"
    ) {
        // FIXED: Determine event type from action using proper enum values
        val eventType = when {
            action.contains("Created", ignoreCase = true) -> AuditEventType.CREATE_ITEM
            action.contains("Updated", ignoreCase = true) -> AuditEventType.UPDATE_ITEM
            action.contains("Deleted", ignoreCase = true) -> AuditEventType.DELETE_ITEM
            action.contains("Accessed", ignoreCase = true) -> AuditEventType.VIEW_ITEM
            action.contains("Viewed", ignoreCase = true) -> AuditEventType.VIEW_ITEM
            else -> AuditEventType.VIEW_ITEM // Default to VIEW_ITEM for read operations
        }

        logUserAction(
            userId = userId,
            username = username,
            eventType = eventType,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            outcome = outcome,
            errorMessage = errorMessage,
            securityLevel = securityLevel
        )
    }

    /**
     * Helper method to create audit entries consistently
     */
    private fun createAuditEntry(
        userId: Int?,
        username: String,
        eventType: AuditEventType,
        action: String,
        resourceType: String?,
        resourceId: String?,
        outcome: AuditOutcome,
        errorMessage: String?,
        securityLevel: String
    ): AuditEntry {
        return AuditEntry(
            userId = userId,
            username = username,
            timestamp = System.currentTimeMillis(),
            eventType = eventType.value,  // ✅ FIXED: Use .value to get string
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            deviceInfo = getDeviceInfo(),
            appVersion = getAppVersion(),
            sessionId = getCurrentSessionId(),
            outcome = outcome.value,  // ✅ FIXED: Use .value to get string
            errorMessage = errorMessage,
            securityLevel = securityLevel
        )
    }

    /**
     * Get current device information
     */
    private fun getDeviceInfo(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})"
    }

    /**
     * Get current app version
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get current session ID (placeholder - should be injected from SessionManager)
     */
    private fun getCurrentSessionId(): String? {
        return try {
            // In a real implementation, this would be injected from SessionManager
            // For now, generate a temporary ID
            UUID.randomUUID().toString().take(8)
        } catch (e: Exception) {
            null
        }
    }
}