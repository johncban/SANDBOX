package com.jcb.passbook

import android.app.Application
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import com.jcb.passbook.util.audit.AuditLogger
import com.jcb.passbook.util.audit.SecurityAuditManager
import com.jcb.passbook.util.logging.DebugLogger
import com.jcb.passbook.util.security.RestrictiveReleaseTree
import com.lambdapioneer.argon2kt.BuildConfig
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import dagger.hilt.components.SingletonComponent

/***    ------------------------------------------ LEGACY CODE ------------------------------------------
@HiltAndroidApp
class CoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
        ------------------------------------------ LEGACY CODE ------------------------------------------     ***/

/**
 * Enhanced CoreApp with comprehensive security initialization,
 * debug logging, and audit system integration.
 */
@HiltAndroidApp
class CoreApp : Application() {
    
    companion object {
        private const val TAG = "CoreApp"
        
        // HARDCODED SECURITY SETTINGS FOR APPLICATION STARTUP
        private const val ENABLE_DEBUG_LOGGING_IN_RELEASE = false // Security: disable debug logs in production
        private const val ENABLE_SECURITY_AUDIT_ON_STARTUP = true // Always enable security auditing
        private const val ENABLE_CRASH_PROTECTION = true // Enable crash protection and logging
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize Timber with security considerations
            initializeLogging()
            
            Timber.i("$TAG: Application startup initiated")
            
            // Initialize security and audit systems
            initializeSecuritySystems()
            
            Timber.i("$TAG: Application startup completed successfully")
            
        } catch (e: Exception) {
            // Critical startup failure - log and handle gracefully
            if (BuildConfig.DEBUG) {
                Timber.e(e, "$TAG: Critical failure during application startup")
            }
            
            // In production, we might want to crash gracefully or show an error
            if (ENABLE_CRASH_PROTECTION) {
                handleStartupFailure(e)
            } else {
                throw e
            }
        }
    }

    /**
     * Initialize logging system with security-aware configuration
     */
    private fun initializeLogging() {
        when {
            BuildConfig.DEBUG -> {
                // Debug build: full logging
                Timber.plant(Timber.DebugTree())
                Timber.d("$TAG: Debug logging enabled")
            }
            ENABLE_DEBUG_LOGGING_IN_RELEASE -> {
                // Release build with debug logging (security risk)
                Timber.plant(Timber.DebugTree())
                Timber.w("$TAG: Debug logging enabled in release build - SECURITY RISK")
            }
            else -> {
                // Release build: restrictive logging
                Timber.plant(RestrictiveReleaseTree())
                Timber.i("$TAG: Restrictive logging enabled")
            }
        }
    }

    /**
     * Initialize security and audit systems
     */
    private fun initializeSecuritySystems() {
        if (!ENABLE_SECURITY_AUDIT_ON_STARTUP) {
            Timber.w("$TAG: Security audit disabled - NOT RECOMMENDED")
            return
        }

        try {
            // Access dependencies via EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(this, CoreAppEntryPoint::class.java)
            val auditLogger = entryPoint.auditLogger()
            val securityAuditManager = entryPoint.securityAuditManager()
            val debugLogger = entryPoint.debugLogger()

            // Initialize debug logger
            debugLogger.initialize(this)
            debugLogger.logSecurity("Application security systems initialization started", "INFO")

            // Start security monitoring
            securityAuditManager.startSecurityMonitoring()
            debugLogger.logSecurity("Security audit manager started", "INFO")

            // Log application startup with comprehensive system information
            auditLogger.logUserAction(
                userId = null,
                username = "SYSTEM",
                eventType = AuditEventType.SYSTEM_EVENT,
                action = "Application started with enhanced security",
                resourceType = "SYSTEM",
                resourceId = "APP_STARTUP",
                outcome = AuditOutcome.SUCCESS,
                securityLevel = "NORMAL"
            )

            // Log security configuration
            auditLogger.logSecurityEvent(
                message = "Security configuration applied - Debug in release: $ENABLE_DEBUG_LOGGING_IN_RELEASE, " +
                         "Security audit: $ENABLE_SECURITY_AUDIT_ON_STARTUP, " +
                         "Crash protection: $ENABLE_CRASH_PROTECTION",
                securityLevel = "INFO",
                outcome = AuditOutcome.SUCCESS
            )
            
            debugLogger.logSecurity("Security systems initialized successfully", "INFO")
            Timber.i("$TAG: Security systems initialized successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize security systems")
            throw SecuritySystemInitializationException("Failed to initialize security systems", e)
        }
    }

    /**
     * Handle startup failure gracefully
     */
    private fun handleStartupFailure(exception: Exception) {
        try {
            // Log the failure if possible
            Timber.e(exception, "$TAG: Startup failure handled gracefully")
            
            // In a real app, you might:
            // 1. Show a user-friendly error dialog
            // 2. Send crash reports to your analytics service
            // 3. Attempt to recover or restart
            // 4. Gracefully degrade functionality
            
            // For this example, we'll just log and continue with minimal functionality
            Timber.w("$TAG: Continuing with minimal functionality due to startup failure")
            
        } catch (e: Exception) {
            // If even error handling fails, we have no choice but to crash
            throw RuntimeException("Critical startup failure and error handling failed", exception)
        }
    }

    /**
     * Custom exception for security system initialization failures
     */
    class SecuritySystemInitializationException(
        message: String,
        cause: Throwable? = null
    ) : RuntimeException(message, cause)
}

/**
 * Entry point interface for dependency injection in Application class
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(SingletonComponent::class)
interface CoreAppEntryPoint {
    fun auditLogger(): AuditLogger
    fun securityAuditManager(): SecurityAuditManager
    fun debugLogger(): DebugLogger
}