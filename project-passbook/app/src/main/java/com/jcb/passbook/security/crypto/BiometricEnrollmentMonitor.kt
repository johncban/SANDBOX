package com.jcb.passbook.security.crypto

import android.content.Context
import androidx.biometric.BiometricManager as AndroidXBiometricManager
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BiometricEnrollmentMonitor tracks changes in biometric enrollment
 * and triggers key regeneration when enrollment changes are detected.
 *
 * FIXED: Now uses lazy AuditLogger provider to break circular dependency
 * - Changed constructor to accept auditLoggerProvider: () -> AuditLogger
 * - AuditLogger is initialized lazily using 'by lazy' delegate
 * - All existing functionality preserved
 */
@Singleton
class BiometricEnrollmentMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLoggerProvider: () -> AuditLogger  // ✅ CHANGED: Function provider instead of direct injection
) {

    // ✅ LAZY INITIALIZATION: AuditLogger created only when first accessed
    private val auditLogger: AuditLogger by lazy { auditLoggerProvider() }

    companion object {
        private const val ENROLLMENT_HASH_KEY = "biometric_enrollment_hash"
        private const val CHECK_INTERVAL_MS = 30_000L // 30 seconds
        private const val TAG = "BiometricEnrollmentMonitor"
    }

    private val prefs = context.getSharedPreferences("biometric_monitor", Context.MODE_PRIVATE)
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _enrollmentChangeDetected = MutableStateFlow(false)
    val enrollmentChangeDetected: StateFlow<Boolean> = _enrollmentChangeDetected.asStateFlow()
    private var isMonitoring = false

    /**
     * Start monitoring biometric enrollment changes
     */
    fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true

        monitorScope.launch {
            // Initial hash capture
            updateStoredEnrollmentHash()

            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                checkForEnrollmentChanges()
            }
        }

        Timber.d("Started biometric enrollment monitoring")
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitorScope.cancel()
        Timber.d("Stopped biometric enrollment monitoring")
    }

    /**
     * Check for enrollment changes
     */
    private suspend fun checkForEnrollmentChanges() {
        try {
            val currentHash = getCurrentEnrollmentHash()
            val storedHash = prefs.getString(ENROLLMENT_HASH_KEY, null)

            if (storedHash != null && storedHash != currentHash) {
                // ✅ Lazy auditLogger is accessed here - initialized on first use
                auditLogger.logSecurityEvent(
                    "Biometric enrollment change detected",
                    "ELEVATED",
                    AuditOutcome.WARNING
                )

                _enrollmentChangeDetected.value = true
                Timber.w("Biometric enrollment change detected")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking biometric enrollment changes")
        }
    }

    /**
     * Update stored enrollment hash after handling changes
     */
    fun updateStoredEnrollmentHash() {
        try {
            val currentHash = getCurrentEnrollmentHash()
            prefs.edit().putString(ENROLLMENT_HASH_KEY, currentHash).apply()
            _enrollmentChangeDetected.value = false

            // ✅ Lazy auditLogger is accessed here
            auditLogger.logUserAction(
                null,
                "SYSTEM",
                AuditEventType.UPDATE,
                "Biometric enrollment hash updated",
                "BIOMETRIC",
                "ENROLLMENT_HASH",
                AuditOutcome.SUCCESS,
                null,
                "NORMAL"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to update enrollment hash")
        }
    }

    /**
     * Get current biometric enrollment hash
     * FIXED: Only use androidx.biometric.BiometricManager for compatibility
     */
    private fun getCurrentEnrollmentHash(): String {
        return try {
            val biometricManager = AndroidXBiometricManager.from(context)
            val enrollmentInfo = StringBuilder()

            // Add biometric availability status
            val biometricStatus = biometricManager.canAuthenticate(
                AndroidXBiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            enrollmentInfo.append("biometric_status:$biometricStatus;")

            // Add device credential status
            val deviceCredentialStatus = biometricManager.canAuthenticate(
                AndroidXBiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            enrollmentInfo.append("device_credential_status:$deviceCredentialStatus;")

            // Add combined authentication status
            val combinedStatus = biometricManager.canAuthenticate(
                AndroidXBiometricManager.Authenticators.BIOMETRIC_STRONG or
                        AndroidXBiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            enrollmentInfo.append("combined_status:$combinedStatus;")

            // Create hash of enrollment info
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(enrollmentInfo.toString().toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get enrollment hash")
            "error_getting_hash_${System.currentTimeMillis()}"
        }
    }

    /**
     * Check if biometric authentication is available
     */
    fun isBiometricAvailable(): Boolean {
        return try {
            val biometricManager = AndroidXBiometricManager.from(context)
            biometricManager.canAuthenticate(
                AndroidXBiometricManager.Authenticators.BIOMETRIC_STRONG or
                        AndroidXBiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == AndroidXBiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Timber.e(e, "Error checking biometric availability")
            false
        }
    }

    /**
     * Get biometric status for audit logging
     */
    fun getBiometricStatusInfo(): String {
        return try {
            val biometricManager = AndroidXBiometricManager.from(context)
            val status = biometricManager.canAuthenticate(
                AndroidXBiometricManager.Authenticators.BIOMETRIC_STRONG or
                        AndroidXBiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

            when (status) {
                AndroidXBiometricManager.BIOMETRIC_SUCCESS -> "Available"
                AndroidXBiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No hardware"
                AndroidXBiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Hardware unavailable"
                AndroidXBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "None enrolled"
                AndroidXBiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security update required"
                AndroidXBiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Unsupported"
                AndroidXBiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "Unknown"
                else -> "Status: $status"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Reset enrollment change detection (call after handling the change)
     */
    fun resetEnrollmentChangeDetection() {
        _enrollmentChangeDetected.value = false
        updateStoredEnrollmentHash()
    }
}
