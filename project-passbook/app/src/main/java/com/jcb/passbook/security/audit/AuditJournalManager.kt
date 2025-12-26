package com.jcb.passbook.security.audit

import android.content.Context
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditJournalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    @Suppress("unused") private val secureMemoryUtils: SecureMemoryUtils
) {
    companion object {
        private const val JOURNAL_FILE_NAME = "audit_journal.log"
        private const val JOURNAL_BACKUP_NAME = "audit_journal_backup.log"
        private const val MAX_JOURNAL_SIZE_MB = 10
        private const val ENTRY_SEPARATOR = "\n---AUDIT_ENTRY---\n"
        @Suppress("unused")
        private const val TAG = "AuditJournalManager"
    }

    private val journalFile = File(context.filesDir, JOURNAL_FILE_NAME)
    private val journalBackup = File(context.filesDir, JOURNAL_BACKUP_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var database: AppDatabase? = null

    fun setDatabase(db: AppDatabase) {
        this.database = db
    }

    suspend fun recordAuditEvent(
        eventType: AuditEventType,
        message: String,
        timestamp: Instant,
        sessionId: String,
        userId: Long,
        metadata: Map<String, String> = emptyMap()
    ) {
        try {
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

            val db = database
            if (db != null) {
                try {
                    db.auditDao().insert(entry)
                    Timber.v("✅ Audit event recorded to database: ${eventType.name}")
                } catch (dbException: Exception) {
                    Timber.w(dbException, "Database write failed, falling back to journal")
                    writeToJournal(entry)
                }
            } else {
                Timber.d("Database not available, writing to journal")
                writeToJournal(entry)
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to record audit event: ${eventType.name}")
        }
    }

    suspend fun getAuditHistory(userId: Long, limit: Int = 100): List<AuditLogEntry> {
        return try {
            val db = database ?: return emptyList()
            val entries: List<AuditEntry> =
                db.auditDao().getAuditEntriesForUser(userId).first().take(limit)
            entries.map { entry ->
                AuditLogEntry(
                    id = entry.id,
                    userId = entry.userId ?: 0L,           // ✅ FIX: Handle nullable Long?
                    eventType = entry.eventType,
                    message = entry.action ?: "",          // ✅ FIX: Handle nullable String?
                    timestamp = Instant.ofEpochMilli(entry.timestamp),
                    sessionId = entry.sessionId ?: "",
                    chainHash = entry.chainHash ?: ""
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get audit history for user $userId")
            emptyList()
        }
    }

    @Deprecated("Only for testing. Never call in production!")
    suspend fun clearAllLogs() {
        try {
            Timber.w("⚠️ CLEARING ALL AUDIT LOGS - testing only!")
            database?.auditDao()?.deleteAll()
            clearJournal()
            Timber.d("✅ All audit logs cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear all audit logs")
        }
    }

    suspend fun getAuditSummary(): AuditSummary {
        return try {
            val db = database ?: return AuditSummary.empty()
            val currentSessionId = sessionManager.getSessionId()
            val totalEntries: Long = db.auditDao().getTotalEntryCount()

            // ✅ FIXED: Call searchAuditEntries with non-nullable parameters
            val searchResults: List<AuditEntry> = db.auditDao().searchAuditEntries(
                userId = -1L,
                eventType = "",
                outcome = "",
                securityLevel = "",
                startTime = 0L,
                endTime = System.currentTimeMillis()
            ).first()

            val entriesThisSession: Long = searchResults
                .count { entry -> entry.sessionId == currentSessionId }
                .toLong()

            val authSuccessEntries: List<AuditEntry> = db.auditDao()
                .getAuditEntriesByType(AuditEventType.AUTHENTICATION_SUCCESS)
                .first()
            val authSuccess: Int = authSuccessEntries.size

            val authFailureEntries: List<AuditEntry> = db.auditDao()
                .getAuditEntriesByType(AuditEventType.AUTHENTICATION_FAILURE)
                .first()
            val authFailure: Int = authFailureEntries.size
            val authAttempts: Long = (authSuccess + authFailure).toLong()

            val violationEntries: List<AuditEntry> = db.auditDao()
                .getAuditEntriesByType(AuditEventType.SECURITY_VIOLATION)
                .first()
            val violations: Long = violationEntries.size.toLong()

            val lastTimestamp: Instant? = db.auditDao().getLatestEntryTimestamp()
                ?.let { millis -> Instant.ofEpochMilli(millis) }

            AuditSummary(
                totalEntries = totalEntries,
                entriesThisSession = entriesThisSession,
                authenticationAttempts = authAttempts,
                securityViolations = violations,
                lastAuditTimestamp = lastTimestamp
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get audit summary")
            AuditSummary.empty()
        }
    }

    suspend fun writeToJournal(entry: AuditEntry) {
        try {
            val esk = sessionManager.getEphemeralSessionKey()
            if (esk == null) {
                writeUnencryptedEntry(entry)
                return
            }

            val entryJson = json.encodeToString(entry)
            val encryptedData = encryptData(entryJson, esk)

            synchronized(this) {
                if (journalFile.exists() &&
                    journalFile.length() > MAX_JOURNAL_SIZE_MB * 1024 * 1024
                ) {
                    rotateJournal()
                }

                RandomAccessFile(journalFile, "rws").use { raf ->
                    raf.channel.lock().use {
                        raf.seek(raf.length())
                        raf.writeBytes("${System.currentTimeMillis()}:")
                        raf.writeBytes(
                            android.util.Base64.encodeToString(
                                encryptedData,
                                android.util.Base64.NO_WRAP
                            )
                        )
                        raf.writeBytes(ENTRY_SEPARATOR)
                    }
                }
            }

            Timber.v("Wrote audit entry to journal: ${entry.action}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to write to audit journal")
            try {
                writeUnencryptedEntry(entry)
            } catch (fallback: Exception) {
                Timber.e(fallback, "Critical: Failed to write audit entry")
            }
        }
    }

    private fun writeUnencryptedEntry(entry: AuditEntry) {
        synchronized(this) {
            RandomAccessFile(journalFile, "rws").use { raf ->
                raf.channel.lock().use {
                    raf.seek(raf.length())
                    raf.writeBytes("${System.currentTimeMillis()}:UNENCRYPTED:")
                    raf.writeBytes(json.encodeToString(entry))
                    raf.writeBytes(ENTRY_SEPARATOR)
                }
            }
        }
    }

    fun clearJournal() {
        try {
            synchronized(this) {
                if (journalFile.exists()) journalFile.delete()
                if (journalBackup.exists()) journalBackup.delete()
            }
            Timber.d("Cleared audit journal")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear audit journal")
        }
    }

    private fun rotateJournal() {
        try {
            if (journalBackup.exists()) journalBackup.delete()
            if (journalFile.exists()) journalFile.renameTo(journalBackup)
            Timber.d("Rotated audit journal")
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate journal")
        }
    }

    private fun encryptData(data: String, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return iv + encrypted
    }

    @Suppress("unused")
    private fun decryptData(encryptedData: ByteArray, key: SecretKeySpec): String? {
        return try {
            if (encryptedData.size <= 12) return null
            val iv = encryptedData.copyOfRange(0, 12)
            val encrypted = encryptedData.copyOfRange(12, encryptedData.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt journal data")
            null
        }
    }

    private fun determineSecurityLevel(eventType: AuditEventType): String {
        return when (eventType) {
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
    }

    data class AuditLogEntry(
        val id: Long,
        val userId: Long,
        val eventType: AuditEventType,
        val message: String,
        val timestamp: Instant,
        val sessionId: String,
        val chainHash: String
    )

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
}
