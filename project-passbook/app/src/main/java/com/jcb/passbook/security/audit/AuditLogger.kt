package com.jcb.passbook.security.audit

import android.content.Context
import android.os.Build
import com.jcb.passbook.data.local.database.entities.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger(
    private val context: Context,
    private val auditQueueProvider: () -> AuditQueue,
    private val auditChainManagerProvider: () -> AuditChainManager,
    private val applicationScope: CoroutineScope
) {
    // ✅ FIX: Lazy initialization from providers
    private val auditQueue: AuditQueue? by lazy { auditQueueProvider() }
    private val auditChainManager: AuditChainManager? by lazy { auditChainManagerProvider() }
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Log user action */
    fun logUserAction(
        userId: Long?,
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
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log user action")
            }
        }
    }

    /** Log security event */
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
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log security event")
            }
        }
    }

    /** Log authentication events */
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
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log authentication")
            }
        }
    }

    /** Log audit verification results */
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
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log audit verification")
            }
        }
    }

    /** Log application lifecycle events */
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
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log app lifecycle")
            }
        }
    }

    /** Log database operation */
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
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log database operation")
            }
        }
    }

    /** Log item operation (CRUD) */
    fun logItemOperation(
        userId: Long,
        username: String,
        operation: AuditEventType,
        itemId: String?,
        outcome: AuditOutcome,
        errorMessage: String? = null
    ) {
        auditScope.launch {
            try {
                val auditEntry = createAuditEntry(
                    userId = userId,
                    username = username,
                    eventType = operation,
                    action = "Item operation: ${operation.name}",
                    resourceType = "ITEM",
                    resourceId = itemId,
                    outcome = outcome,
                    errorMessage = errorMessage,
                    securityLevel = "NORMAL"
                )
                val chainedEntry = auditChainManager?.addEntryToChain(auditEntry) ?: auditEntry
                auditQueue?.enqueue(chainedEntry)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log item operation")
            }
        }
    }

    /** Log data access event */
    fun logDataAccess(
        userId: Long,
        username: String,
        action: String,
        resourceType: String?,
        resourceId: String?,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        errorMessage: String? = null,
        securityLevel: String = "NORMAL"
    ) {
        val eventType = when {
            action.contains("Created", true) -> AuditEventType.CREATE_ITEM
            action.contains("Updated", true) -> AuditEventType.UPDATE_ITEM
            action.contains("Deleted", true) -> AuditEventType.DELETE_ITEM
            action.contains("Accessed", true) -> AuditEventType.VIEW_ITEM
            action.contains("Viewed", true) -> AuditEventType.VIEW_ITEM
            else -> AuditEventType.VIEW_ITEM
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

    /** Helper method to create audit entries consistently */
    private fun createAuditEntry(
        userId: Long?,
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
            eventType = eventType,
            action = action,
            description = action,
            value = resourceId,
            resourceType = resourceType,
            resourceId = resourceId,
            deviceInfo = getDeviceInfo(),
            appVersion = getAppVersion(),
            sessionId = getCurrentSessionId(),
            outcome = outcome,
            errorMessage = errorMessage,
            securityLevel = securityLevel,
            chainPrevHash = null,
            chainHash = null,
            checksum = null
        )
    }

    private fun getDeviceInfo(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})"

    private fun getAppVersion(): String = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "${packageInfo.versionName} ($versionCode)"
    } catch (e: Exception) {
        "Unknown"
    }

    private fun getCurrentSessionId(): String = try {
        UUID.randomUUID().toString()
    } catch (e: Exception) {
        "unknown-session"
    }

    // ---- Specific event-typed wrappers ----
    fun logLogin(userId: Long, username: String, outcome: AuditOutcome) =
        logUserAction(userId, username, AuditEventType.LOGIN, "User login", "AUTH", userId.toString(), outcome, securityLevel = "ELEVATED")

    fun logLogout(userId: Long, username: String) =
        logUserAction(userId, username, AuditEventType.LOGOUT, "User logout", "AUTH", userId.toString(), AuditOutcome.SUCCESS)

    fun logAuthenticationFailure(username: String, reason: String) =
        logUserAction(null, username, AuditEventType.AUTHENTICATION_FAILURE, "Authentication failed", "AUTH", null, AuditOutcome.FAILURE, reason, "HIGH")

    fun logKeyRotation(userId: Long, username: String, outcome: AuditOutcome) =
        logUserAction(userId, username, AuditEventType.KEY_ROTATION, "Database key rotation", "ENCRYPTION", "MASTER_KEY", outcome, securityLevel = "CRITICAL")

    fun logItemCreated(userId: Long, username: String, itemId: Long, outcome: AuditOutcome) =
        logUserAction(userId, username, AuditEventType.CREATE_ITEM, "Item created", "ITEM", itemId.toString(), outcome)

    fun logItemUpdated(userId: Long, username: String, itemId: Long, outcome: AuditOutcome) =
        logUserAction(userId, username, AuditEventType.UPDATE_ITEM, "Item updated", "ITEM", itemId.toString(), outcome)

    fun logItemDeleted(userId: Long, username: String, itemId: Long, outcome: AuditOutcome) =
        logUserAction(userId, username, AuditEventType.DELETE_ITEM, "Item deleted", "ITEM", itemId.toString(), outcome)

    fun logItemViewed(userId: Long, username: String, itemId: Long) =
        logUserAction(userId, username, AuditEventType.VIEW_ITEM, "Item viewed", "ITEM", itemId.toString(), AuditOutcome.SUCCESS)

    // ✅ FIXED: Changed AuditOutcome.ERROR to AuditOutcome.FAILURE (line 343)
    fun logSecurityBreach(message: String, severity: String = "CRITICAL") =
        logSecurityEvent(message, severity, AuditOutcome.FAILURE, errorMessage = "Security breach detected")

    fun logAccessDenied(userId: Long?, username: String, resource: String, reason: String) =
        logUserAction(userId, username, AuditEventType.ACCESS_DENIED, "Access denied to $resource", "SECURITY", resource, AuditOutcome.FAILURE, reason, "HIGH")
}
