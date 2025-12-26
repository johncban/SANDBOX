package com.jcb.passbook.security.audit

import android.content.Context
import android.util.Log
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class MasterAuditLogger(
    private val context: Context,
    private val auditJournalManager: AuditJournalManager,
    private val auditChainManager: AuditChainManager,
    private val sessionManager: SessionManager,
    private val secureMemoryUtils: SecureMemoryUtils
) {

    private companion object {
        const val TAG = "PassBook:MasterAuditLog"
        const val LOG_VERSION = 1
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun logAuditEvent(
        eventType: AuditEventType,
        message: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        scope.launch {
            try {
                if (!sessionManager.isSessionActive()) {
                    Log.e(TAG, "Cannot log audit event: session not active")
                    return@launch
                }

                val timestamp = Instant.now()
                val sessionId = sessionManager.getSessionId()
                val userId = sessionManager.getUserId()

                // Record to journal
                auditJournalManager.recordAuditEvent(
                    eventType = eventType,
                    message = message,
                    timestamp = timestamp,
                    sessionId = sessionId,
                    userId = userId,
                    metadata = metadata
                )

                // Create entry for chain
                val entry = AuditEntry(
                    id = 0L,
                    userId = userId,
                    action = message,
                    eventType = eventType,
                    timestamp = timestamp.toEpochMilli(),
                    description = metadata.toString(),
                    outcome = AuditOutcome.SUCCESS,
                    securityLevel = determineSecurityLevel(eventType),
                    sessionId = sessionId,
                    ipAddress = null,
                    deviceInfo = null,
                    checksum = null,
                    chainHash = null,
                    chainPrevHash = null
                )

                auditChainManager.addEntryToChain(entry)

                secureMemoryUtils.wipeByteArray(
                    metadata.values.joinToString().toByteArray()
                )

                Log.d(TAG, "✅ Audit event logged: ${eventType.name}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to log audit event", e)
            }
        }
    }

    fun logAuthenticationEvent(
        success: Boolean,
        method: String,
        userId: String? = null
    ) {
        logAuditEvent(
            eventType = if (success)
                AuditEventType.AUTHENTICATION_SUCCESS
            else
                AuditEventType.AUTHENTICATION_FAILURE,
            message = "Authentication via $method",
            metadata = mapOfNotNull(
                "method" to method,
                "userId" to userId,
                "success" to success.toString()
            )
        )
    }

    fun logEncryptionEvent(
        operation: String,
        itemCount: Int = 1,
        success: Boolean = true
    ) {
        logAuditEvent(
            eventType = AuditEventType.ENCRYPTION_OPERATION,
            message = "$operation on $itemCount item(s)",
            metadata = mapOf(
                "operation" to operation,
                "itemCount" to itemCount.toString(),
                "success" to success.toString()
            )
        )
    }

    fun logSecurityViolation(
        violationType: String,
        details: String,
        severity: String = "MEDIUM"
    ) {
        logAuditEvent(
            eventType = AuditEventType.SECURITY_VIOLATION,
            message = "$violationType: $details",
            metadata = mapOf(
                "type" to violationType,
                "severity" to severity,
                "details" to details
            )
        )
    }

    fun getAuditHistory(
        userId: Long,
        limit: Int = 100,
        onResult: (List<AuditJournalManager.AuditLogEntry>) -> Unit
    ) {
        scope.launch {
            try {
                val history = auditJournalManager.getAuditHistory(userId, limit)
                onResult(history)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retrieve audit history", e)
                onResult(emptyList())
            }
        }
    }

    // ✅ FIXED: Made this a suspend function or launch coroutine
    fun verifyAuditChainIntegrity(onResult: (Boolean) -> Unit) {
        scope.launch {
            try {
                val result = auditChainManager.verifyChain()
                onResult(result.isValid)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify audit chain integrity", e)
                onResult(false)
            }
        }
    }

    @Deprecated("Only for testing. Never call in production!")
    fun clearAuditLogs() {
        scope.launch {
            try {
                Log.w(TAG, "⚠️ CLEARING AUDIT LOGS - testing only!")
                auditJournalManager.clearAllLogs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear audit logs", e)
            }
        }
    }

    fun getAuditSummary(onResult: (AuditJournalManager.AuditSummary) -> Unit) {
        scope.launch {
            try {
                val summary = auditJournalManager.getAuditSummary()
                onResult(summary)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get audit summary", e)
                onResult(AuditJournalManager.AuditSummary.empty())
            }
        }
    }

    private fun determineSecurityLevel(eventType: AuditEventType): String =
        when (eventType) {
            AuditEventType.AUTHENTICATION_FAILURE,
            AuditEventType.SECURITY_VIOLATION,
            AuditEventType.UNAUTHORIZED_ACCESS -> "CRITICAL"
            AuditEventType.AUTHENTICATION_SUCCESS,
            AuditEventType.ENCRYPTION_OPERATION,
            AuditEventType.DATA_ACCESS -> "HIGH"
            AuditEventType.DATA_MODIFICATION,
            AuditEventType.CONFIGURATION_CHANGE -> "MEDIUM"
            else -> "LOW"
        }

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>?): Map<String, String> =
        pairs.filterNotNull()
            .mapNotNull { (k, v) -> if (v != null) k to v else null }
            .toMap()
}
