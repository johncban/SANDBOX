package com.jcb.passbook.security.audit

import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuditVerificationService performs periodic integrity verification of the audit trail
 * and provides on-demand verification capabilities.
 */
@Singleton
class AuditVerificationService @Inject constructor(
    private val auditDao: AuditDao,
    private val auditChainManager: AuditChainManager,
    private val AuditLogger: AuditLogger
) {
    companion object {
        private const val VERIFICATION_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
        private const val BATCH_VERIFICATION_SIZE = 1000
        private const val TAG = "AuditVerificationService"
    }

    private val verificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _verificationResults = MutableStateFlow<VerificationStatus>(VerificationStatus.NotStarted)
    val verificationResults: StateFlow<VerificationStatus> = _verificationResults.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * Start periodic audit verification
     */
    fun startPeriodicVerification() {
        if (_isRunning.value) return

        _isRunning.value = true
        verificationScope.launch {
            while (isActive) {
                try {
                    performFullVerification()
                    delay(VERIFICATION_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in periodic audit verification")
                    delay(60_000) // Wait 1 minute before retry
                }
            }
        }

        Timber.i("Started periodic audit verification")
    }

    /**
     * Stop periodic verification
     */
    fun stopPeriodicVerification() {
        _isRunning.value = false
        verificationScope.cancel()
        Timber.i("Stopped periodic audit verification")
    }

    /**
     * Perform on-demand verification of the entire audit trail
     */
    suspend fun performFullVerification(): VerificationResult {
        return try {
            _verificationResults.value = VerificationStatus.Running("Starting full verification...")

            val oldestTimestamp = auditDao.getOldestEntryTimestamp() ?: return VerificationResult.NoEntries
            val latestTimestamp = auditDao.getLatestEntryTimestamp() ?: return VerificationResult.NoEntries

            _verificationResults.value = VerificationStatus.Running("Verifying chain integrity...")

            val chainVerification = auditChainManager.verifyChain(oldestTimestamp, latestTimestamp)
            val result = when (chainVerification) {
                is AuditChainManager.ChainVerificationResult.Success -> {
                    _verificationResults.value = VerificationStatus.Running("Performing anomaly detection...")
                    val anomalies = detectAnomalies(oldestTimestamp, latestTimestamp)

                    VerificationResult.Success(
                        entriesVerified = chainVerification.verifiedEntries,
                        timeRange = oldestTimestamp to latestTimestamp,
                        anomalies = anomalies,
                        warnings = chainVerification.warnings
                    )
                }
                is AuditChainManager.ChainVerificationResult.Compromised -> {
                    VerificationResult.ChainCompromised(
                        entriesVerified = chainVerification.verifiedEntries,
                        discrepancies = chainVerification.discrepancies.map { discrepancy ->
                            when (discrepancy) {
                                is AuditChainManager.ChainDiscrepancy.BrokenChain ->
                                    "Broken chain at entry ${discrepancy.entryId}: ${discrepancy.details}"
                                is AuditChainManager.ChainDiscrepancy.InvalidHash ->
                                    "Invalid hash at entry ${discrepancy.entryId}: ${discrepancy.details}"
                                is AuditChainManager.ChainDiscrepancy.InvalidChecksum ->
                                    "Invalid checksum at entry ${discrepancy.entryId}: ${discrepancy.details}"
                            }
                        }
                    )
                }
                is AuditChainManager.ChainVerificationResult.Error -> {
                    VerificationResult.Error(chainVerification.message)
                }
            }

            // Log verification result
            val outcome = when (result) {
                is VerificationResult.Success -> AuditOutcome.SUCCESS
                is VerificationResult.ChainCompromised -> AuditOutcome.FAILURE
                else -> AuditOutcome.WARNING
            }

            AuditLogger.logAuditVerification(
                result = result.javaClass.simpleName,
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
     * Verify a specific time range
     */
    suspend fun verifyTimeRange(startTime: Long, endTime: Long): VerificationResult {
        return try {
            _verificationResults.value = VerificationStatus.Running("Verifying time range...")

            val chainVerification = auditChainManager.verifyChain(startTime, endTime)
            val result = when (chainVerification) {
                is AuditChainManager.ChainVerificationResult.Success -> {
                    val anomalies = detectAnomalies(startTime, endTime)
                    VerificationResult.Success(
                        entriesVerified = chainVerification.verifiedEntries,
                        timeRange = startTime to endTime,
                        anomalies = anomalies,
                        warnings = chainVerification.warnings
                    )
                }
                is AuditChainManager.ChainVerificationResult.Compromised -> {
                    VerificationResult.ChainCompromised(
                        entriesVerified = chainVerification.verifiedEntries,
                        discrepancies = chainVerification.discrepancies.map {
                            "Entry ${it.javaClass.simpleName}: ${when (it) {
                                is AuditChainManager.ChainDiscrepancy.BrokenChain -> it.details
                                is AuditChainManager.ChainDiscrepancy.InvalidHash -> it.details
                                is AuditChainManager.ChainDiscrepancy.InvalidChecksum -> it.details
                            }}"
                        }
                    )
                }
                is AuditChainManager.ChainVerificationResult.Error -> {
                    VerificationResult.Error(chainVerification.message)
                }
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
     * Detect anomalies in audit data
     */
    private suspend fun detectAnomalies(startTime: Long, endTime: Long): List<AnomalyReport> {
        val anomalies = mutableListOf<AnomalyReport>()

        try {
            // Check for excessive failed logins
            val failedLogins = auditDao.countAllEventsSince(
                AuditEventType.AUTHENTICATION_FAILURE.value,
                endTime - TimeUnit.HOURS.toMillis(1)
            )
            if (failedLogins > 10) {
                anomalies.add(
                    AnomalyReport.ExcessiveFailedLogins(
                        count = failedLogins,
                        timeWindow = "1 hour"
                    )
                )
            }

            // Check for unusual activity spikes
            val recentActivity = auditDao.countAllEventsSince(
                AuditEventType.CREATE.value,
                endTime - TimeUnit.MINUTES.toMillis(10)
            )
            if (recentActivity > 50) {
                anomalies.add(
                    AnomalyReport.ActivitySpike(
                        eventType = "CREATE",
                        count = recentActivity,
                        timeWindow = "10 minutes"
                    )
                )
            }

            // Check for gaps in timestamps (potential deletion)
            val entries = auditDao.getAuditEntriesInTimeRange(startTime, endTime).value
                .sortedBy { it.timestamp }

            var expectedSequentialGaps = 0
            for (i in 1 until entries.size) {
                val timeDiff = entries[i].timestamp - entries[i-1].timestamp
                if (timeDiff > TimeUnit.HOURS.toMillis(6)) { // More than 6 hours gap
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
     * Get verification statistics
     */
    suspend fun getVerificationStatistics(): VerificationStatistics {
        return try {
            val totalEntries = auditDao.getTotalEntryCount()
            val entriesWithoutChecksum = auditDao.countEntriesWithoutChecksum()
            val entriesWithoutChain = auditDao.countEntriesWithoutChainHash()
            val criticalEvents = auditDao.getCriticalSecurityEvents(100).value.size

            VerificationStatistics(
                totalEntries = totalEntries,
                entriesWithoutChecksum = entriesWithoutChecksum,
                entriesWithoutChainHash = entriesWithoutChain,
                criticalEventsCount = criticalEvents,
                lastVerificationTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get verification statistics")
            VerificationStatistics(
                totalEntries = 0,
                entriesWithoutChecksum = 0,
                entriesWithoutChainHash = 0,
                criticalEventsCount = 0,
                lastVerificationTime = 0
            )
        }
    }

    // Data classes for verification results
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

    sealed class VerificationStatus {
        object NotStarted : VerificationStatus()
        data class Running(val progress: String) : VerificationStatus()
        data class Healthy(val result: VerificationResult.Success) : VerificationStatus()
        data class Compromised(val result: VerificationResult.ChainCompromised) : VerificationStatus()
        data class Error(val message: String) : VerificationStatus()
    }

    sealed class AnomalyReport {
        data class ExcessiveFailedLogins(val count: Int, val timeWindow: String) : AnomalyReport()
        data class ActivitySpike(val eventType: String, val count: Int, val timeWindow: String) : AnomalyReport()
        data class TimestampGaps(val gapCount: Int, val details: String) : AnomalyReport()
        data class AnalysisError(val message: String) : AnomalyReport()
    }

    data class VerificationStatistics(
        val totalEntries: Long,
        val entriesWithoutChecksum: Int,
        val entriesWithoutChainHash: Int,
        val criticalEventsCount: Int,
        val lastVerificationTime: Long
    )
}