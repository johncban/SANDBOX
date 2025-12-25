package com.jcb.passbook.core.security

import java.util.concurrent.TimeUnit

/**
 * SecurityPolicy defines the security configuration and policies for the application.
 * This centralizes security settings and makes them easily configurable.
 */
object SecurityPolicy {

    // Session Management
    const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    const val SESSION_WARNING_BEFORE_TIMEOUT_MS = 30 * 1000L // 30 seconds
    const val MAX_CONCURRENT_SESSIONS = 1

    // Authentication
    const val BIOMETRIC_AUTH_TIMEOUT_SECONDS = 60
    const val MAX_AUTH_ATTEMPTS = 5
    const val AUTH_LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    const val REQUIRE_BIOMETRIC_FOR_CRITICAL_OPS = true

    // Key Management
    const val MASTER_KEY_ROTATION_INTERVAL_DAYS = 90
    const val DATABASE_REKEY_INTERVAL_DAYS = 30
    const val EPHEMERAL_KEY_SIZE_BYTES = 32
    const val MASTER_KEY_SIZE_BYTES = 32

    // Memory Security
    const val SECURE_WIPE_PASSES = 2
    const val CLIPBOARD_AUTO_CLEAR_MS = 30 * 1000L // 30 seconds
    const val DISABLE_CLIPBOARD_IN_RELEASE = true

    // Device Security
    const val BLOCK_ROOTED_DEVICES = true
    const val BLOCK_DEBUGGABLE_APPS = true
    const val BLOCK_EMULATORS = false // Allow for development
    const val DETECT_FRIDA = true
    const val DEVICE_CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    // Database Security
    const val SQLCIPHER_ITERATIONS = 256000
    const val DATABASE_BACKUP_ENCRYPTION = true
    const val ENABLE_WAL_MODE = true

    // Audit Logging
    const val AUDIT_RETENTION_DAYS = 90
    const val AUDIT_VERIFICATION_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    const val AUDIT_BATCH_SIZE = 50
    const val AUDIT_JOURNAL_MAX_SIZE_MB = 10
    const val ENABLE_AUDIT_CHAIN_VERIFICATION = true

    // Network Security (for future features)
    const val CERTIFICATE_PINNING = true
    const val TLS_MIN_VERSION = "TLSv1.3"
    const val NETWORK_TIMEOUT_MS = 30 * 1000L

    // UI Security
    const val PREVENT_SCREENSHOTS = true
    const val OBSCURE_IN_RECENT_APPS = true
    const val AUTO_LOCK_ON_BACKGROUND = true
    const val BLUR_SENSITIVE_CONTENT = true

    // Development/Debug Settings
    const val ALLOW_DEBUG_LOGGING = false // Override in debug builds
    const val ENABLE_SECURITY_LOGGING = true
    const val CRASH_ON_SECURITY_VIOLATION = false // Enable for development

    // Derived Properties (calculated from above constants)
    val SESSION_TIMEOUT_MINUTES: Long get() = TimeUnit.MILLISECONDS.toMinutes(SESSION_TIMEOUT_MS)
    val AUTH_LOCKOUT_MINUTES: Long get() = TimeUnit.MILLISECONDS.toMinutes(AUTH_LOCKOUT_DURATION_MS)
    val AUDIT_RETENTION_MS: Long get() = TimeUnit.DAYS.toMillis(AUDIT_RETENTION_DAYS.toLong())
    val MASTER_KEY_ROTATION_MS: Long get() = TimeUnit.DAYS.toMillis(MASTER_KEY_ROTATION_INTERVAL_DAYS.toLong())
    val DATABASE_REKEY_MS: Long get() = TimeUnit.DAYS.toMillis(DATABASE_REKEY_INTERVAL_DAYS.toLong())

    /**
     * Security risk levels for different operations
     */
    enum class RiskLevel {
        LOW,      // Basic operations, read access
        MEDIUM,   // Modify data, settings changes
        HIGH,     // Key operations, export/import
        CRITICAL  // System administration, security config
    }

    /**
     * Get authentication requirements for a given risk level
     */
    fun getAuthRequirement(riskLevel: RiskLevel): AuthRequirement {
        return when (riskLevel) {
            RiskLevel.LOW -> AuthRequirement.SESSION_VALID
            RiskLevel.MEDIUM -> AuthRequirement.RECENT_AUTH
            RiskLevel.HIGH -> AuthRequirement.BIOMETRIC_REQUIRED
            RiskLevel.CRITICAL -> AuthRequirement.BIOMETRIC_REQUIRED
        }
    }

    /**
     * Get timeout for authentication requirement
     */
    fun getAuthTimeout(riskLevel: RiskLevel): Long {
        return when (riskLevel) {
            RiskLevel.LOW -> SESSION_TIMEOUT_MS
            RiskLevel.MEDIUM -> 2 * 60 * 1000L // 2 minutes
            RiskLevel.HIGH -> 30 * 1000L // 30 seconds
            RiskLevel.CRITICAL -> 15 * 1000L // 15 seconds
        }
    }

    enum class AuthRequirement {
        SESSION_VALID,      // Just need an active session
        RECENT_AUTH,        // Need authentication within timeout
        BIOMETRIC_REQUIRED  // Always require biometric/device credential
    }

    /**
     * Security configuration for different build types
     */
    fun getConfigurationForBuild(isDebug: Boolean): SecurityConfiguration {
        return if (isDebug) {
            SecurityConfiguration(
                enforceRootDetection = false,
                requireBiometricAuth = false,
                enableSecurityLogging = true,
                allowScreenshots = true,
                sessionTimeoutMs = 10 * 60 * 1000L, // 10 minutes for development
                auditVerificationEnabled = true
            )
        } else {
            SecurityConfiguration(
                enforceRootDetection = BLOCK_ROOTED_DEVICES,
                requireBiometricAuth = REQUIRE_BIOMETRIC_FOR_CRITICAL_OPS,
                enableSecurityLogging = ENABLE_SECURITY_LOGGING,
                allowScreenshots = !PREVENT_SCREENSHOTS,
                sessionTimeoutMs = SESSION_TIMEOUT_MS,
                auditVerificationEnabled = ENABLE_AUDIT_CHAIN_VERIFICATION
            )
        }
    }

    data class SecurityConfiguration(
        val enforceRootDetection: Boolean,
        val requireBiometricAuth: Boolean,
        val enableSecurityLogging: Boolean,
        val allowScreenshots: Boolean,
        val sessionTimeoutMs: Long,
        val auditVerificationEnabled: Boolean
    )

    /**
     * Validate security policy consistency
     */
    fun validateConfiguration(): List<String> {
        val issues = mutableListOf<String>()

        if (SESSION_TIMEOUT_MS < 60_000) {
            issues.add("Session timeout too short (minimum 1 minute recommended)")
        }

        if (SESSION_TIMEOUT_MS > 30 * 60 * 1000L) {
            issues.add("Session timeout too long (maximum 30 minutes recommended)")
        }

        if (AUDIT_RETENTION_DAYS < 30) {
            issues.add("Audit retention too short (minimum 30 days recommended)")
        }

        if (MASTER_KEY_ROTATION_INTERVAL_DAYS > 365) {
            issues.add("Master key rotation interval too long (maximum 1 year recommended)")
        }

        if (MAX_AUTH_ATTEMPTS < 3) {
            issues.add("Max auth attempts too restrictive")
        }

        if (MAX_AUTH_ATTEMPTS > 10) {
            issues.add("Max auth attempts too permissive")
        }

        return issues
    }
}