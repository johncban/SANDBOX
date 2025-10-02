package com.jcb.passbook.util.security

/**
 * Security constants and configuration values for the PassBook application.
 * Contains hardcoded security decisions and policy configurations.
 */
object SecurityConstants {
    
    // ================== ROOT DETECTION CONFIGURATION ==================
    
    /**
     * HARDCODED SECURITY DECISION: Allow user override of security warnings
     * Set to false in production for maximum security
     */
    const val ALLOW_USER_SECURITY_OVERRIDE = true
    
    /**
     * HARDCODED SECURITY DECISION: Enable strict security mode
     * When true, no user overrides are allowed regardless of threat level
     */
    const val STRICT_SECURITY_MODE = false
    
    /**
     * HARDCODED SECURITY DECISION: Automatically exit on critical threats
     * When true, app immediately exits on critical security threats
     */
    const val AUTO_EXIT_ON_CRITICAL_THREATS = true
    
    /**
     * HARDCODED SECURITY DECISION: Show detailed technical information to users
     * When true, security dialogs show technical detection details
     */
    const val SHOW_TECHNICAL_SECURITY_DETAILS = true
    
    /**
     * HARDCODED SECURITY DECISION: Maximum number of user overrides allowed
     * After this limit, user loses override privileges
     */
    const val MAX_USER_OVERRIDE_ATTEMPTS = 3
    
    // ================== MONITORING CONFIGURATION ==================
    
    /**
     * HARDCODED SECURITY DECISION: Periodic security check interval (milliseconds)
     * Standard interval for routine security checks
     */
    const val PERIODIC_SECURITY_CHECK_INTERVAL_MS = 300000L // 5 minutes
    
    /**
     * HARDCODED SECURITY DECISION: Rapid security check interval (milliseconds)
     * Used when elevated threats are detected
     */
    const val RAPID_SECURITY_CHECK_INTERVAL_MS = 60000L // 1 minute
    
    /**
     * HARDCODED SECURITY DECISION: Auto-dismiss timeout for critical dialogs (milliseconds)
     * Critical security dialogs automatically dismiss after this time
     */
    const val CRITICAL_DIALOG_AUTO_DISMISS_MS = 30000L // 30 seconds
    
    // ================== LOGGING CONFIGURATION ==================
    
    /**
     * HARDCODED SECURITY DECISION: Enable file logging
     * When true, debug logs are written to files
     */
    const val ENABLE_FILE_LOGGING = true
    
    /**
     * HARDCODED SECURITY DECISION: Enable security debug logging
     * When true, detailed security events are logged
     */
    const val ENABLE_SECURITY_DEBUG_LOGGING = true
    
    /**
     * HARDCODED SECURITY DECISION: Log user interactions with security dialogs
     * When true, all user responses to security warnings are logged
     */
    const val LOG_USER_SECURITY_INTERACTIONS = true
    
    /**
     * HARDCODED SECURITY DECISION: Maximum log file size (MB)
     * Log files larger than this are automatically deleted
     */
    const val MAX_LOG_FILE_SIZE_MB = 10
    
    /**
     * HARDCODED SECURITY DECISION: Maximum number of log files to keep
     * Older log files are deleted when this limit is exceeded
     */
    const val MAX_LOG_FILES_TO_KEEP = 5
    
    // ================== THREAT LEVEL THRESHOLDS ==================
    
    /**
     * HARDCODED SECURITY DECISION: Threshold for considering multiple indicators as HIGH threat
     * Number of detection methods that trigger HIGH threat level
     */
    const val HIGH_THREAT_DETECTION_THRESHOLD = 3
    
    /**
     * HARDCODED SECURITY DECISION: Threshold for considering indicators as CRITICAL threat
     * Number of critical detection methods that trigger CRITICAL threat level
     */
    const val CRITICAL_THREAT_DETECTION_THRESHOLD = 1
    
    // ================== SECURITY POLICY ENFORCEMENT ==================
    
    /**
     * HARDCODED SECURITY DECISION: Enable security lockdown mode
     * When true, repeated override attempts trigger permanent lockdown
     */
    const val ENABLE_SECURITY_LOCKDOWN = true
    
    /**
     * HARDCODED SECURITY DECISION: Require confirmation for security overrides
     * When true, users must confirm they understand the risks before overriding
     */
    const val REQUIRE_OVERRIDE_CONFIRMATION = true
    
    /**
     * HARDCODED SECURITY DECISION: Enable Play Integrity API checks
     * When true, Google Play Integrity API is used for additional verification
     */
    const val ENABLE_PLAY_INTEGRITY_CHECKS = false // Disabled - requires backend integration
    
    // ================== DEBUG AND DEVELOPMENT ==================
    
    /**
     * HARDCODED SECURITY DECISION: Enable debug logging in release builds
     * WARNING: This is a security risk and should always be false in production
     */
    const val ENABLE_DEBUG_IN_RELEASE = false
    
    /**
     * HARDCODED SECURITY DECISION: Enable crash protection during startup
     * When true, startup failures are handled gracefully instead of crashing
     */
    const val ENABLE_STARTUP_CRASH_PROTECTION = true
    
    /**
     * HARDCODED SECURITY DECISION: Enable comprehensive system information logging
     * When true, detailed device and system information is logged at startup
     */
    const val ENABLE_SYSTEM_INFO_LOGGING = true
    
    // ================== SECURITY MESSAGES ==================
    
    /**
     * Standard security messages for different threat levels
     */
    object Messages {
        const val LOW_THREAT = "Potential security risk detected. Some root indicators found."
        const val MEDIUM_THREAT = "Moderate security risk detected. Multiple root indicators found."
        const val HIGH_THREAT = "High security risk detected. Strong evidence of root access."
        const val CRITICAL_THREAT = "CRITICAL SECURITY THREAT: Definitive root access detected. Application will terminate for security."
        
        const val OVERRIDE_WARNING = "Continuing may expose sensitive data to security risks. Are you sure you want to proceed?"
        const val LOCKDOWN_WARNING = "Maximum security override attempts exceeded. Security lockdown activated."
        const val INITIALIZATION_FAILED = "Security system initialization failed. Application cannot continue safely."
    }
    
    // ================== VALIDATION METHODS ==================
    
    /**
     * Validate security configuration at runtime
     */
    fun validateSecurityConfiguration(): List<String> {
        val issues = mutableListOf<String>()
        
        if (ENABLE_DEBUG_IN_RELEASE) {
            issues.add("DEBUG_IN_RELEASE is enabled - CRITICAL SECURITY RISK")
        }
        
        if (!ENABLE_SECURITY_LOCKDOWN && ALLOW_USER_SECURITY_OVERRIDE) {
            issues.add("Security lockdown disabled with user overrides enabled - SECURITY RISK")
        }
        
        if (MAX_USER_OVERRIDE_ATTEMPTS > 5) {
            issues.add("Too many override attempts allowed - SECURITY RISK")
        }
        
        if (!AUTO_EXIT_ON_CRITICAL_THREATS) {
            issues.add("Auto-exit on critical threats disabled - SECURITY RISK")
        }
        
        return issues
    }
    
    /**
     * Get current security policy summary
     */
    fun getSecurityPolicySummary(): String {
        return buildString {
            appendLine("=== SECURITY POLICY SUMMARY ===")
            appendLine("User Override Allowed: $ALLOW_USER_SECURITY_OVERRIDE")
            appendLine("Strict Mode: $STRICT_SECURITY_MODE")
            appendLine("Auto-Exit Critical: $AUTO_EXIT_ON_CRITICAL_THREATS")
            appendLine("Show Technical Details: $SHOW_TECHNICAL_SECURITY_DETAILS")
            appendLine("Max Override Attempts: $MAX_USER_OVERRIDE_ATTEMPTS")
            appendLine("Security Lockdown: $ENABLE_SECURITY_LOCKDOWN")
            appendLine("Override Confirmation: $REQUIRE_OVERRIDE_CONFIRMATION")
            appendLine("File Logging: $ENABLE_FILE_LOGGING")
            appendLine("Security Debug Logging: $ENABLE_SECURITY_DEBUG_LOGGING")
            appendLine("Debug in Release: $ENABLE_DEBUG_IN_RELEASE")
            appendLine("============================")
        }
    }
}