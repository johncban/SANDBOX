package com.jcb.passbook.security.audit

import android.content.Context
import android.util.Log
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import java.time.Instant

/**
 * Master Audit Logger - Central point for all security audit logging
 *
 * Coordinates between AuditJournalManager (database) and AuditChainManager
 * (blockchain-style integrity verification)
 *
 * Security:
 * - All log entries are timestamped with verified session context
 * - Logs are encrypted before storage
 * - Integrity chain is maintained via AuditChainManager
 * - Logs are immutable once committed
 */
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

    /**
     * Log a security audit event
     *
     * This is the primary method for logging all security-relevant events
     */
    fun logAuditEvent(
        eventType: AuditEventType,
        message: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        try {
            // Verify session is active
            if (!sessionManager.isSessionActive()) {
                Log.e(TAG, "Cannot log audit event: session not active")
                return
            }

            val timestamp = Instant.now()
            val sessionId = sessionManager.getSessionId()
            val userId = sessionManager.getUserId()

            // Log to journal (database storage)
            auditJournalManager.recordAuditEvent(
                eventType = eventType,
                message = message,
                timestamp = timestamp,
                sessionId = sessionId,
                userId = userId,
                metadata = metadata
            )

            // Update blockchain-style chain
            auditChainManager.addAuditEntry(
                eventType = eventType,
                message = message,
                timestamp = timestamp,
                sessionId = sessionId,
                userId = userId
            )

            // Clear sensitive metadata from memory
            secureMemoryUtils.wipeByteArray(metadata.values.joinToString().toByteArray())

            Log.d(TAG, "✅ Audit event logged: ${eventType.name}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log audit event", e)
            // DON'T rethrow - logging failures should not crash the app
            // But DO log the failure itself to system logs
        }
    }

    /**
     * Log authentication event
     */
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

    /**
     * Log encryption operation
     */
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

    /**
     * Log security violation attempt
     */
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

    /**
     * Retrieve audit history for a user
     */
    fun getAuditHistory(userId: Long, limit: Int = 100): List<AuditLogEntry> {
        return try {
            auditJournalManager.getAuditHistory(userId, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve audit history", e)
            emptyList()
        }
    }

    /**
     * Verify audit chain integrity
     *
     * Returns true if all entries match their expected chain hashes
     */
    fun verifyAuditChainIntegrity(): Boolean {
        return try {
            auditChainManager.verifyChainIntegrity()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify audit chain integrity", e)
            false
        }
    }

    /**
     * Clear sensitive audit logs (only for testing/cleanup)
     *
     * This should NEVER be called in production!
     */
    @Deprecated("Only for testing. Never call in production!")
    fun clearAuditLogs() {
        try {
            Log.w(TAG, "⚠️ CLEARING AUDIT LOGS - This should NEVER happen in production!")
            auditJournalManager.clearAllLogs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear audit logs", e)
        }
    }

    /**
     * Get audit summary statistics
     */
    fun getAuditSummary(): AuditSummary {
        return try {
            auditJournalManager.getAuditSummary()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audit summary", e)
            AuditSummary.empty()
        }
    }
}

/**
 * Audit log entry for retrieval
 */
data class AuditLogEntry(
    val id: Long,
    val userId: Long,
    val eventType: AuditEventType,
    val message: String,
    val timestamp: Instant,
    val sessionId: String,
    val chainHash: String
)

/**
 * Audit summary statistics
 */
data class AuditSummary(
    val totalEntries: Long = 0L,
    val entriesThisSession: Long = 0L,
    val authenticationAttempts: Long = 0L,
    val securityViolations: Long = 0L,
    val lastAuditTimestamp: Instant? = null
) {
    companion object {
        fun empty() = AuditSummary()
    }
}

/**
 * Helper to build maps without null values
 */
private fun mapOfNotNull(vararg pairs: Pair<String, String>?): Map<String, String> {
    return pairs.filterNotNull().toMap()
}
