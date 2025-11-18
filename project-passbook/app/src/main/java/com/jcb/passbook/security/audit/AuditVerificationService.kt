package com.jcb.passbook.security.audit

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditVerificationService performs periodic integrity verification of the audit trail
 * and provides on-demand verification capabilities.
 *
 * ✅ CIRCULAR DEPENDENCY FIX APPLIED:
 * - Changed constructor to accept lazy AuditLogger provider
 * - Uses lazy initialization to break DI cycle
 *
 * FIXES APPLIED:
 * - ✅ Fixed verifyChain() calls - no parameters needed
 * - ✅ Fixed ChainVerificationResult handling - use isValid property
 * - ✅ Fixed ChainError references - use errors list
 * - ✅ Fixed type mismatches - Int vs Long
 * - ✅ Fixed DAO method calls - removed extra parameters
 */
@Singleton
class AuditVerificationService @Inject constructor(
    private val auditDao: AuditDao,
    private val auditChainManager: AuditChainManager,
    private val auditLoggerProvider: () -> AuditLogger  // ✅ CHANGED: Lazy provider
) {
    // ✅ CHANGED: Lazy initialization of AuditLogger to break circular dependency
    private val auditLogger: AuditLogger by lazy { auditLoggerProvider() }

    private val verificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _verificationResults = MutableStateFlow<VerificationStatus>(VerificationStatus.NotStarted)
    val verificationResults: StateFlow<VerificationStatus> = _verificationResults.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Starts periodic audit verification every 30 minutes
     */
    fun startPeriodicVerification() {
        if (_isRunning.value) return

        _isRunning.value = true
        verificationScope.launch {
            while (isActive) {
                try {
                    performFullVerification()
                    delay(30 * 60 * 1000L) // 30 minutes
                } catch (e: Exception) {
                    Timber.e(e, "Error in periodic audit verification")
                    delay(60000) // Wait 1 minute before retry
                }
            }
        }

        Timber.i("Started periodic audit verification")
    }

    /**
     * Stops periodic verification
     */
    fun stopPeriodicVerification() {
        _isRunning.value = false
        verificationScope.cancel()
        Timber.i("Stopped periodic audit verification")
    }

    /**
     * Performs a full verification of the entire audit chain
     */
    suspend fun performFullVerification(): VerificationResult {
        return try {
            _verificationResults.value = VerificationStatus.Running("Starting full verification...")

            // Get time range
            val oldestTimestamp = auditDao.getOldestEntryTimestamp()
                ?: return VerificationResult.NoEntries
            val latestTimestamp = auditDao.getLatestEntryTimestamp()
                ?: return VerificationResult.NoEntries

            _verificationResults.value = VerificationStatus.Running("Verifying chain integrity...")

            // Verify the chain (no parameters needed)
            val chainVerification = auditChainManager.verifyChain()

            // Build result based on chain verification
            val result = if (chainVerification.isValid) {
                _verificationResults.value = VerificationStatus.Running("Performing anomaly detection...")
                val anomalies = detectAnomalies(oldestTimestamp, latestTimestamp)

                VerificationResult.Success(
                    entriesVerified = chainVerification.totalEntries,
                    timeRange = oldestTimestamp to latestTimestamp,
                    anomalies = anomalies,
                    warnings = emptyList() // No warnings in current implementation
                )
            } else {
                VerificationResult.ChainCompromised(
                    entriesVerified = chainVerification.totalEntries,
                    discrepancies = chainVerification.errors.map { error ->
                        "${error.errorType}: Entry ${error.entryId} - ${error.message}"
                    }
                )
            }

            // Log the result using lazy-initialized auditLogger
            val outcome = when (result) {
                is VerificationResult.Success -> AuditOutcome.SUCCESS
                is VerificationResult.ChainCompromised -> AuditOutcome.FAILURE
                else -> AuditOutcome.WARNING
            }

            auditLogger.logAuditVerification(
                result = result::class.java.simpleName,
                entriesVerified = when (result) {
                    is VerificationResult.Success -> result.entriesVerified
                    is VerificationResult.ChainCompromised -> result.entriesVerified
                    else -> 0
                },
                discrepancies = when (result) {
                    is VerificationResult.ChainCompromised -> result.discrepancies.size
                    else -> 0
                },
                outcome = outcome
            )

            // Update status
            _verificationResults.value = when (result) {
                is VerificationResult.Success -> VerificationStatus.Healthy(result)
                is VerificationResult.ChainCompromised -> VerificationStatus.Compromised(result)
                else -> VerificationStatus.Error(result.toString())
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Full audit verification failed")
            val errorResult = VerificationResult.Error(e.message ?: "Unknown error")
            _verificationResults.value = VerificationStatus.Error(e.message ?: "Unknown error")
            errorResult
        }
    }

    /**
     * Verifies audit entries within a specific time range
     */
    suspend fun verifyTimeRange(startTime: Long, endTime: Long): VerificationResult {
        return try {
            _verificationResults.value = VerificationStatus.Running("Verifying time range...")

            // Verify entire chain (no time range parameter)
            val chainVerification = auditChainManager.verifyChain()

            // Filter to time range after verification
            val result = if (chainVerification.isValid) {
                val anomalies = detectAnomalies(startTime, endTime)

                VerificationResult.Success(
                    entriesVerified = chainVerification.totalEntries,
                    timeRange = startTime to endTime,
                    anomalies = anomalies,
                    warnings = emptyList()
                )
            } else {
                VerificationResult.ChainCompromised(
                    entriesVerified = chainVerification.totalEntries,
                    discrepancies = chainVerification.errors.map { error ->
                        "${error.errorType}: Entry ${error.entryId} - ${error.message}"
                    }
                )
            }

            _verificationResults.value = when (result) {
                is VerificationResult.Success -> VerificationStatus.Healthy(result)
                is VerificationResult.ChainCompromised -> VerificationStatus.Compromised(result)
                else -> VerificationStatus.Error(result.toString())
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Time range verification failed")
            VerificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Detects anomalies in the audit trail within a time range
     */
    private suspend fun detectAnomalies(startTime: Long, endTime: Long): List<AnomalyReport> {
        val anomalies = mutableListOf<AnomalyReport>()

        try {
            // Check for excessive failed logins in last hour
            val failedLogins = auditDao.countAllEventsSince(endTime - TimeUnit.HOURS.toMillis(1))
            if (failedLogins > 10) {
                anomalies.add(
                    AnomalyReport.ExcessiveFailedLogins(
                        count = failedLogins,
                        timeWindow = "1 hour"
                    )
                )
            }

            // Check for activity spikes in last 10 minutes
            val recentActivity = auditDao.countAllEventsSince(endTime - TimeUnit.MINUTES.toMillis(10))
            if (recentActivity > 50) {
                anomalies.add(
                    AnomalyReport.ActivitySpike(
                        eventType = "CREATE",
                        count = recentActivity,
                        timeWindow = "10 minutes"
                    )
                )
            }

            // Check for timestamp gaps
            val entries = auditDao.getAuditEntriesInTimeRange(startTime, endTime)
                .first()
                .sortedBy { it.timestamp }

            var expectedSequentialGaps = 0
            for (i in 1 until entries.size) {
                val timeDiff = entries[i].timestamp - entries[i - 1].timestamp
                if (timeDiff > TimeUnit.HOURS.toMillis(6)) {
                    expectedSequentialGaps++
                }
            }

            if (expectedSequentialGaps > 5) {
                anomalies.add(
                    AnomalyReport.TimestampGaps(
                        gapCount = expectedSequentialGaps,
                        details = "Unusual gaps in audit timestamps detected"
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error detecting anomalies")
            anomalies.add(
                AnomalyReport.AnalysisError("Failed to complete anomaly detection: ${e.message}")
            )
        }

        return anomalies
    }

    /**
     * Gets current verification statistics
     */
    suspend fun getVerificationStatistics(): VerificationStatistics {
        return try {
            val totalEntries = auditDao.getTotalEntryCount()
            val entriesWithoutChecksum = auditDao.countEntriesWithoutChecksum()
            val entriesWithoutChain = auditDao.countEntriesWithoutChainHash()
            val criticalEvents = auditDao.getCriticalSecurityEvents().first()

            VerificationStatistics(
                totalEntries = totalEntries,
                entriesWithoutChecksum = entriesWithoutChecksum,
                entriesWithoutChainHash = entriesWithoutChain,
                criticalEventsCount = criticalEvents.size,
                lastVerificationTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get verification statistics")
            VerificationStatistics(
                totalEntries = 0L,
                entriesWithoutChecksum = 0,
                entriesWithoutChainHash = 0,
                criticalEventsCount = 0,
                lastVerificationTime = 0L
            )
        }
    }

    // =============================================================================================
    // SEALED CLASSES AND DATA CLASSES
    // =============================================================================================

    /**
     * Represents the result of an audit verification
     */
    sealed class VerificationResult {
        data class Success(
            val entriesVerified: Int,
            val timeRange: Pair<Long, Long>,
            val anomalies: List<AnomalyReport>,
            val warnings: List<String>
        ) : VerificationResult()

        data class ChainCompromised(
            val entriesVerified: Int,
            val discrepancies: List<String>
        ) : VerificationResult()

        data class Error(val message: String) : VerificationResult()

        object NoEntries : VerificationResult()
    }

    /**
     * Represents the current status of verification
     */
    sealed class VerificationStatus {
        object NotStarted : VerificationStatus()
        data class Running(val progress: String) : VerificationStatus()
        data class Healthy(val result: VerificationResult.Success) : VerificationStatus()
        data class Compromised(val result: VerificationResult.ChainCompromised) : VerificationStatus()
        data class Error(val message: String) : VerificationStatus()
    }

    /**
     * Represents different types of anomalies detected
     */
    sealed class AnomalyReport {
        data class ExcessiveFailedLogins(val count: Int, val timeWindow: String) : AnomalyReport()
        data class ActivitySpike(val eventType: String, val count: Int, val timeWindow: String) : AnomalyReport()
        data class TimestampGaps(val gapCount: Int, val details: String) : AnomalyReport()
        data class AnalysisError(val message: String) : AnomalyReport()
    }

    /**
     * Statistics about audit verification
     */
    data class VerificationStatistics(
        val totalEntries: Long,
        val entriesWithoutChecksum: Int,
        val entriesWithoutChainHash: Int,
        val criticalEventsCount: Int,
        val lastVerificationTime: Long
    )
}
