package com.jcb.passbook.util.security

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import com.jcb.passbook.util.audit.AuditLogger
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Security Manager with comprehensive threat detection,
 * user interaction policies, and detailed security logging.
 */
@Singleton
class EnhancedSecurityManager @Inject constructor(
    private val enhancedRootDetector: EnhancedRootDetector,
    private val securityDialogManager: SecurityDialogManager,
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TAG = "EnhancedSecurityManager"
        
        // HARDCODED SECURITY POLICIES
        private const val PERIODIC_CHECK_INTERVAL_MS = 300000L // 5 minutes
        private const val RAPID_CHECK_INTERVAL_MS = 60000L // 1 minute for high-risk scenarios
        private const val MAX_OVERRIDE_ATTEMPTS = 3
        private const val SECURITY_LOCKDOWN_ENABLED = true
        private const val AUTO_EXIT_ON_CRITICAL_THREATS = true
    }

    data class SecurityState(
        val isCompromised: Boolean = false,
        val lastCheckTimestamp: Long = 0L,
        val threatLevel: EnhancedRootDetector.SecurityLevel = EnhancedRootDetector.SecurityLevel.LOW,
        val detectionMethods: List<String> = emptyList(),
        val userOverrideCount: Int = 0,
        val isLockdownActive: Boolean = false
    )

    private val _securityState = mutableStateOf(SecurityState())
    val securityState: State<SecurityState> = _securityState

    private var monitoringJob: Job? = null
    private var isInitialized = false
    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initialize security monitoring with comprehensive startup checks
     */
    suspend fun initializeSecurityMonitoring(context: Context) {
        if (isInitialized) {
            Timber.w("$TAG: Security monitoring already initialized")
            return
        }

        Timber.i("$TAG: Initializing enhanced security monitoring")
        auditLogger.logSecurityEvent(
            "Enhanced security monitoring initialization started",
            "INFO",
            AuditOutcome.SUCCESS
        )

        try {
            // Perform initial comprehensive security check
            val initialResult = enhancedRootDetector.performEnhancedRootDetection(context)
            updateSecurityState(initialResult)

            // Start periodic monitoring
            startPeriodicMonitoring(context)
            
            isInitialized = true
            
            auditLogger.logSecurityEvent(
                "Enhanced security monitoring initialized successfully - Initial threat level: ${initialResult.severity}",
                "INFO",
                AuditOutcome.SUCCESS
            )
            
            Timber.i("$TAG: Security monitoring initialized - Threat level: ${initialResult.severity}")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize security monitoring")
            auditLogger.logSecurityEvent(
                "Security monitoring initialization failed: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            throw e
        }
    }

    /**
     * Start periodic security monitoring with adaptive intervals
     */
    private fun startPeriodicMonitoring(context: Context) {
        monitoringJob?.cancel()
        
        monitoringJob = monitoringScope.launch {
            while (isActive) {
                try {
                    val result = enhancedRootDetector.performEnhancedRootDetection(context)
                    updateSecurityState(result)
                    
                    // Adaptive monitoring interval based on threat level
                    val interval = when (result.severity) {
                        EnhancedRootDetector.SecurityLevel.LOW -> PERIODIC_CHECK_INTERVAL_MS
                        EnhancedRootDetector.SecurityLevel.MEDIUM -> PERIODIC_CHECK_INTERVAL_MS / 2
                        EnhancedRootDetector.SecurityLevel.HIGH -> RAPID_CHECK_INTERVAL_MS
                        EnhancedRootDetector.SecurityLevel.CRITICAL -> RAPID_CHECK_INTERVAL_MS / 2
                    }
                    
                    delay(interval)
                    
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error during periodic security check")
                    auditLogger.logSecurityEvent(
                        "Periodic security check failed: ${e.message}",
                        "WARNING",
                        AuditOutcome.FAILURE
                    )
                    delay(PERIODIC_CHECK_INTERVAL_MS) // Fallback interval
                }
            }
        }
    }

    /**
     * Perform immediate security check and handle results
     */
    suspend fun performImmediateSecurityCheck(
        context: Context,
        onSecurityThreat: ((EnhancedRootDetector.RootDetectionResult) -> Unit)? = null
    ): EnhancedRootDetector.RootDetectionResult {
        Timber.d("$TAG: Performing immediate security check")
        
        auditLogger.logSecurityEvent(
            "Immediate security check requested",
            "INFO",
            AuditOutcome.SUCCESS
        )

        val result = enhancedRootDetector.performEnhancedRootDetection(context)
        updateSecurityState(result)

        if (result.isRooted) {
            handleSecurityThreat(result)
            onSecurityThreat?.invoke(result)
        }

        return result
    }

    /**
     * Handle detected security threats with user interaction
     */
    suspend fun handleSecurityThreat(
        result: EnhancedRootDetector.RootDetectionResult,
        onUserOverride: (() -> Unit)? = null,
        onSecurityExit: (() -> Unit)? = null
    ) {
        Timber.w("$TAG: Handling security threat - Severity: ${result.severity}, Override allowed: ${result.allowUserOverride}")
        
        auditLogger.logSecurityEvent(
            "Security threat detected and being handled - Severity: ${result.severity}",
            when (result.severity) {
                EnhancedRootDetector.SecurityLevel.LOW -> "INFO"
                EnhancedRootDetector.SecurityLevel.MEDIUM -> "WARNING"
                EnhancedRootDetector.SecurityLevel.HIGH -> "ELEVATED"
                EnhancedRootDetector.SecurityLevel.CRITICAL -> "CRITICAL"
            },
            AuditOutcome.WARNING
        )

        // HARDCODED SECURITY DECISION: Auto-exit for critical threats
        if (AUTO_EXIT_ON_CRITICAL_THREATS && result.severity == EnhancedRootDetector.SecurityLevel.CRITICAL) {
            auditLogger.logSecurityEvent(
                "Critical security threat detected - Auto-exit policy triggered",
                "CRITICAL",
                AuditOutcome.BLOCKED
            )
            onSecurityExit?.invoke()
            return
        }

        // Check if user has exceeded override attempts
        if (_securityState.value.userOverrideCount >= MAX_OVERRIDE_ATTEMPTS) {
            auditLogger.logSecurityEvent(
                "Maximum security override attempts exceeded - Forcing exit",
                "CRITICAL",
                AuditOutcome.BLOCKED
            )
            activateSecurityLockdown()
            onSecurityExit?.invoke()
            return
        }

        // Show security dialog with user interaction options
        securityDialogManager.showRootDetectionDialog(
            result = result,
            onDismiss = {
                auditLogger.logUserAction(
                    userId = null,
                    username = "USER",
                    eventType = AuditEventType.SECURITY_EVENT,
                    action = "Security dialog dismissed - App exit",
                    resourceType = "SECURITY_RESPONSE",
                    resourceId = "THREAT_RESPONSE",
                    outcome = AuditOutcome.SUCCESS,
                    securityLevel = "INFO"
                )
                onSecurityExit?.invoke()
            },
            onOverride = if (result.allowUserOverride) {
                {
                    handleUserOverride(result)
                    onUserOverride?.invoke()
                }
            } else null
        )
    }

    /**
     * Handle user override decision with security logging
     */
    private suspend fun handleUserOverride(result: EnhancedRootDetector.RootDetectionResult) {
        val newOverrideCount = _securityState.value.userOverrideCount + 1
        
        _securityState.value = _securityState.value.copy(
            userOverrideCount = newOverrideCount
        )
        
        auditLogger.logUserAction(
            userId = null,
            username = "USER",
            eventType = AuditEventType.SECURITY_EVENT,
            action = "User overrode security warning - Attempt $newOverrideCount/$MAX_OVERRIDE_ATTEMPTS",
            resourceType = "SECURITY_OVERRIDE",
            resourceId = "THREAT_OVERRIDE_${result.severity}",
            outcome = AuditOutcome.WARNING,
            securityLevel = when (result.severity) {
                EnhancedRootDetector.SecurityLevel.LOW -> "WARNING"
                EnhancedRootDetector.SecurityLevel.MEDIUM -> "ELEVATED"
                EnhancedRootDetector.SecurityLevel.HIGH -> "CRITICAL"
                EnhancedRootDetector.SecurityLevel.CRITICAL -> "CRITICAL"
            }
        )
        
        Timber.w("$TAG: User override accepted - Count: $newOverrideCount/$MAX_OVERRIDE_ATTEMPTS")
        
        // Check if approaching limit
        if (newOverrideCount >= MAX_OVERRIDE_ATTEMPTS - 1) {
            auditLogger.logSecurityEvent(
                "User approaching maximum override attempts - Next override will trigger lockdown",
                "WARNING",
                AuditOutcome.WARNING
            )
        }
    }

    /**
     * Activate security lockdown mode
     */
    private suspend fun activateSecurityLockdown() {
        if (!SECURITY_LOCKDOWN_ENABLED) return
        
        _securityState.value = _securityState.value.copy(
            isLockdownActive = true
        )
        
        auditLogger.logSecurityEvent(
            "Security lockdown activated - All override privileges revoked",
            "CRITICAL",
            AuditOutcome.BLOCKED
        )
        
        Timber.e("$TAG: Security lockdown activated")
    }

    /**
     * Update internal security state
     */
    private fun updateSecurityState(result: EnhancedRootDetector.RootDetectionResult) {
        _securityState.value = _securityState.value.copy(
            isCompromised = result.isRooted,
            lastCheckTimestamp = result.timestamp,
            threatLevel = result.severity,
            detectionMethods = result.detectionMethods
        )
    }

    /**
     * Stop security monitoring
     */
    fun stopSecurityMonitoring() {
        Timber.i("$TAG: Stopping security monitoring")
        monitoringJob?.cancel()
        isInitialized = false
        
        auditLogger.logSecurityEvent(
            "Security monitoring stopped",
            "INFO",
            AuditOutcome.SUCCESS
        )
    }

    /**
     * Get current security dialog state
     */
    fun getDialogState() = securityDialogManager.dialogState

    /**
     * Reset override count (for testing or admin functions)
     */
    suspend fun resetOverrideCount() {
        _securityState.value = _securityState.value.copy(
            userOverrideCount = 0,
            isLockdownActive = false
        )
        
        auditLogger.logSecurityEvent(
            "Security override count reset",
            "INFO",
            AuditOutcome.SUCCESS
        )
    }

    /**
     * Get detailed security status for debugging
     */
    fun getSecurityStatus(): String {
        val state = _securityState.value
        return buildString {
            appendLine("Enhanced Security Manager Status:")
            appendLine("- Compromised: ${state.isCompromised}")
            appendLine("- Threat Level: ${state.threatLevel}")
            appendLine("- Override Count: ${state.userOverrideCount}/$MAX_OVERRIDE_ATTEMPTS")
            appendLine("- Lockdown Active: ${state.isLockdownActive}")
            appendLine("- Last Check: ${java.util.Date(state.lastCheckTimestamp)}")
            appendLine("- Detection Methods: ${state.detectionMethods.joinToString(", ")}")
            appendLine("- Monitoring Active: ${isInitialized && monitoringJob?.isActive == true}")
        }
    }
}