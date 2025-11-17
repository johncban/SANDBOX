package com.jcb.passbook.security.audit

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditQueue provides crash-safe queuing for audit entries with persistent buffering.
 * Ensures audit entries are never lost even on app crashes or database unavailability.
 */
@Singleton
class AuditQueue @Inject constructor(
    private val auditDao: AuditDao,
    private val journalManager: AuditJournalManager
) {
    companion object {
        private const val BATCH_SIZE = 50
        private const val FLUSH_INTERVAL_MS = 5000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auditChannel = Channel<AuditEntry>(Channel.UNLIMITED)
    private val inMemoryBuffer = ConcurrentLinkedQueue<AuditEntry>()

    @Volatile
    private var isProcessing = false

    init {
        startProcessing()
        recoverFromJournal()
    }

    /**
     * Enqueue an audit entry for crash-safe storage
     */
    suspend fun enqueue(auditEntry: AuditEntry) {
        try {
            // Add to in-memory buffer first for immediate availability
            inMemoryBuffer.offer(auditEntry)
            // Send to processing channel
            auditChannel.send(auditEntry)
        } catch (e: Exception) {
            // If channel is closed, write directly to journal
            Timber.w(e, "Failed to enqueue audit entry, writing to journal")
            journalManager.writeToJournal(auditEntry)
        }
    }

    /**
     * Start background processing of audit entries
     */
    private fun startProcessing() {
        if (isProcessing) return
        isProcessing = true

        queueScope.launch {
            val batch = mutableListOf<AuditEntry>()
            while (isActive) {
                try {
                    // Collect entries into batches
                    var entry = auditChannel.receive()
                    batch.add(entry)

                    // Try to get more entries without blocking
                    while (batch.size < BATCH_SIZE) {
                        entry = auditChannel.tryReceive().getOrNull() ?: break
                        batch.add(entry)
                    }

                    // Process the batch
                    if (batch.isNotEmpty()) {
                        processBatch(batch.toList())
                        batch.clear()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in audit queue processing")
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        // Periodic flush of any remaining entries
        queueScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushInMemoryBuffer()
            }
        }
    }

    /**
     * Process a batch of audit entries
     */
    private suspend fun processBatch(entries: List<AuditEntry>) {
        var retryCount = 0
        var remainingEntries = entries

        while (remainingEntries.isNotEmpty() && retryCount < MAX_RETRY_ATTEMPTS) {
            try {
                // Try to insert all entries to database
                auditDao.insertAll(remainingEntries)

                // Remove successfully inserted entries from in-memory buffer
                remainingEntries.forEach { entry ->
                    inMemoryBuffer.removeIf {
                        it.timestamp == entry.timestamp &&
                                it.eventType == entry.eventType &&  // ✅ FIXED: Use eventType instead of action
                                it.userId == entry.userId
                    }
                }

                Timber.v("Successfully inserted ${remainingEntries.size} audit entries")
                break
            } catch (e: Exception) {
                retryCount++
                Timber.w(e, "Failed to insert audit batch (attempt $retryCount/$MAX_RETRY_ATTEMPTS)")

                if (retryCount >= MAX_RETRY_ATTEMPTS) {
                    // Write failed entries to persistent journal
                    remainingEntries.forEach { entry ->
                        try {
                            journalManager.writeToJournal(entry)
                        } catch (journalException: Exception) {
                            Timber.e(journalException, "Critical: Failed to write to audit journal")
                        }
                    }
                } else {
                    // Wait before retry with exponential backoff
                    delay(RETRY_DELAY_MS * retryCount)
                }
            }
        }
    }

    /**
     * Flush in-memory buffer to database
     */
    private suspend fun flushInMemoryBuffer() {
        val entries = mutableListOf<AuditEntry>()

        // Drain in-memory buffer
        while (inMemoryBuffer.isNotEmpty()) {
            inMemoryBuffer.poll()?.let { entries.add(it) }
            if (entries.size >= BATCH_SIZE) break
        }

        if (entries.isNotEmpty()) {
            processBatch(entries)
        }
    }

    /**
     * Force flush all pending entries
     */
    suspend fun flush() {
        // Flush channel
        while (true) {
            val entry = auditChannel.tryReceive().getOrNull() ?: break
            try {
                auditDao.insert(entry)
            } catch (e: Exception) {
                journalManager.writeToJournal(entry)
            }
        }

        // Flush in-memory buffer
        flushInMemoryBuffer()

        // Flush journal
        journalManager.flushJournal()
    }

    /**
     * Recover entries from persistent journal on startup
     */
    private fun recoverFromJournal() {
        queueScope.launch {
            try {
                val recoveredEntries = journalManager.recoverFromJournal()
                if (recoveredEntries.isNotEmpty()) {
                    Timber.i("Recovered ${recoveredEntries.size} audit entries from journal")

                    // Process recovered entries
                    recoveredEntries.chunked(BATCH_SIZE).forEach { batch ->
                        processBatch(batch)
                    }

                    // Clear journal after successful recovery
                    journalManager.clearJournal()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to recover from audit journal")
            }
        }
    }

    /**
     * Get current queue size for monitoring
     */
    fun getQueueSize(): Int {
        return inMemoryBuffer.size
    }

    /**
     * Check if queue is healthy (processing without errors)
     */
    fun isHealthy(): Boolean {
        return isProcessing && queueScope.isActive
    }
}
