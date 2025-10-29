package com.jcb.passbook.security.audit

import android.content.Context
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    private val auditDao: AuditDao,
    private val context: Context
) {
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Log a general audit event
     */
    fun logEvent(
        eventType: AuditEventType,
        userId: Int? = null,
        outcome: AuditOutcome,
        eventDescription: String,
        username: String? = null,
        sessionId: String? = null,
        metadata: String? = null
    ) {
        auditScope.launch {
            try {
                val auditEntry = AuditEntry(
                    userId = userId,
                    eventType = eventType.name,
                    outcome = outcome.name,
                    eventDescription = eventDescription,
                    username = username,
                    sessionId = sessionId,
                    metadata = metadata,
                    timestamp = System.currentTimeMillis(),
                    checksum = null // Will be calculated if needed
                )

                auditDao.insert(auditEntry)
                Timber.d("Audit event logged: $eventType - $outcome")
            } catch (e: Exception) {
                Timber.e(e, "Failed to log audit event: $eventType")
            }
        }
    }

    /**
     * Log a security-specific event
     */
    fun logSecurityEvent(
        eventDescription: String,
        severity: String,
        outcome: AuditOutcome,
        userId: Int? = null,
        username: String? = null,
        sessionId: String? = null,
        metadata: String? = null
    ) {
        logEvent(
            eventType = AuditEventType.SECURITY_EVENT,
            userId = userId,
            outcome = outcome,
            eventDescription = eventDescription,
            username = username,
            sessionId = sessionId,
            metadata = "severity:$severity${if (metadata != null) ";$metadata" else ""}"
        )
    }

    /**
     * Log authentication events
     */
    fun logAuthenticationEvent(
        eventType: AuditEventType,
        username: String,
        outcome: AuditOutcome,
        userId: Int? = null,
        errorMessage: String? = null
    ) {
        val description = when (eventType) {
            AuditEventType.LOGIN -> "User login attempt"
            AuditEventType.LOGOUT -> "User logout"
            AuditEventType.AUTHENTICATION_FAILURE -> "Authentication failed${if (errorMessage != null) ": $errorMessage" else ""}"
            else -> "Authentication event"
        }

        logEvent(
            eventType = eventType,
            userId = userId,
            outcome = outcome,
            eventDescription = description,
            username = username
        )
    }

    /**
     * Log CRUD operations
     */
    fun logCrudEvent(
        eventType: AuditEventType,
        userId: Int,
        entityType: String,
        entityId: String? = null,
        outcome: AuditOutcome,
        details: String? = null
    ) {
        val description = "${eventType.name.lowercase()} operation on $entityType${if (entityId != null) " (ID: $entityId)" else ""}${if (details != null) " - $details" else ""}"

        logEvent(
            eventType = eventType,
            userId = userId,
            outcome = outcome,
            eventDescription = description,
            metadata = "entityType:$entityType${if (entityId != null) ";entityId:$entityId" else ""}"
        )
    }

    /**
     * Log system events
     */
    fun logSystemEvent(
        eventDescription: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        metadata: String? = null
    ) {
        logEvent(
            eventType = AuditEventType.SYSTEM_EVENT,
            userId = null,
            outcome = outcome,
            eventDescription = eventDescription,
            metadata = metadata
        )
    }
}