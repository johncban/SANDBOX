package com.jcb.passbook.security.audit

import android.content.Context
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditJournalManager handles persistent journaling of audit entries
 * when the database is unavailable, ensuring no audit data is lost.
 */
@Singleton
class AuditJournalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    companion object {
        private const val JOURNAL_FILE_NAME = "audit_journal.log"
        private const val JOURNAL_BACKUP_NAME = "audit_journal_backup.log"
        private const val MAX_JOURNAL_SIZE_MB = 10
        private const val ENTRY_SEPARATOR = "\n---AUDIT_ENTRY---\n"
        private const val TAG = "AuditJournalManager"
    }

    private val journalFile = File(context.filesDir, JOURNAL_FILE_NAME)
    private val journalBackup = File(context.filesDir, JOURNAL_BACKUP_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Write audit entry to encrypted journal file
     */
    suspend fun writeToJournal(entry: AuditEntry) {
        try {
            val esk = sessionManager.getEphemeralSessionKey()
            if (esk == null) {
                // If no session key, write unencrypted (fallback for critical cases)
                writeUnencryptedEntry(entry)
                return
            }

            val entryJson = json.encodeToString(entry)
            val encryptedData = encryptData(entryJson, esk)

            synchronized(this) {
                // Check file size and rotate if needed
                if (journalFile.exists() && journalFile.length() > MAX_JOURNAL_SIZE_MB * 1024 * 1024) {
                    rotateJournal()
                }

                RandomAccessFile(journalFile, "rws").use { raf ->
                    raf.channel.lock().use { lock ->
                        raf.seek(raf.length())
                        raf.writeBytes("${System.currentTimeMillis()}:")
                        raf.writeBytes(android.util.Base64.encodeToString(encryptedData, android.util.Base64.NO_WRAP))
                        raf.writeBytes(ENTRY_SEPARATOR)
                    }
                }
            }

            Timber.v("Wrote audit entry to journal: ${entry.action}")

        } catch (e: Exception) {
            Timber.e(e, "Failed to write to audit journal")
            // Last resort - try unencrypted
            try {
                writeUnencryptedEntry(entry)
            } catch (fallbackException: Exception) {
                Timber.e(fallbackException, "Critical: Failed to write audit entry to journal even unencrypted")
            }
        }
    }

    /**
     * Write unencrypted entry (fallback for critical cases)
     */
    private fun writeUnencryptedEntry(entry: AuditEntry) {
        synchronized(this) {
            RandomAccessFile(journalFile, "rws").use { raf ->
                raf.channel.lock().use { lock ->
                    raf.seek(raf.length())
                    raf.writeBytes("${System.currentTimeMillis()}:UNENCRYPTED:")
                    raf.writeBytes(json.encodeToString(entry))
                    raf.writeBytes(ENTRY_SEPARATOR)
                }
            }
        }
    }

    /**
     * Recover audit entries from journal
     */
    suspend fun recoverFromJournal(): List<AuditEntry> {
        val recoveredEntries = mutableListOf<AuditEntry>()

        try {
            if (!journalFile.exists()) {
                return emptyList()
            }

            val content = journalFile.readText()
            val entries = content.split(ENTRY_SEPARATOR).filter { it.isNotBlank() }

            val esk = sessionManager.getEphemeralSessionKey()

            entries.forEach { entryData ->
                try {
                    val parts = entryData.split(":", limit = 3)
                    if (parts.size >= 2) {
                        val timestamp = parts[0].toLongOrNull()

                        val entryJson = when {
                            parts.size == 3 && parts[1] == "UNENCRYPTED" -> {
                                parts[2]
                            }
                            esk != null -> {
                                val encryptedData = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                                decryptData(encryptedData, esk)
                            }
                            else -> {
                                Timber.w("Cannot decrypt journal entry - no session key")
                                null
                            }
                        }

                        entryJson?.let { jsonStr ->
                            val entry = json.decodeFromString<AuditEntry>(jsonStr)
                            recoveredEntries.add(entry)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to recover journal entry: ${entryData.take(100)}")
                }
            }

            Timber.i("Recovered ${recoveredEntries.size} entries from audit journal")

        } catch (e: Exception) {
            Timber.e(e, "Failed to recover from audit journal")
        }

        return recoveredEntries
    }

    /**
     * Clear the journal file after successful recovery
     */
    fun clearJournal() {
        try {
            synchronized(this) {
                if (journalFile.exists()) {
                    journalFile.delete()
                }
                if (journalBackup.exists()) {
                    journalBackup.delete()
                }
            }
            Timber.d("Cleared audit journal")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear audit journal")
        }
    }

    /**
     * Flush any pending writes to journal
     */
    suspend fun flushJournal() {
        // Force filesystem sync - journal writes are already synchronous
        try {
            if (journalFile.exists()) {
                RandomAccessFile(journalFile, "rws").use { raf ->
                    raf.fd.sync()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to flush journal")
        }
    }

    /**
     * Rotate journal when it gets too large
     */
    private fun rotateJournal() {
        try {
            if (journalBackup.exists()) {
                journalBackup.delete()
            }

            if (journalFile.exists()) {
                journalFile.renameTo(journalBackup)
            }

            Timber.d("Rotated audit journal")
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate journal")
        }
    }

    /**
     * Encrypt data with ESK
     */
    private fun encryptData(data: String, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        return iv + encrypted
    }

    /**
     * Decrypt data with ESK
     */
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

    /**
     * Get journal file size for monitoring
     */
    fun getJournalSize(): Long {
        return try {
            if (journalFile.exists()) journalFile.length() else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if journal exists and has content
     */
    fun hasJournalContent(): Boolean {
        return try {
            journalFile.exists() && journalFile.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}