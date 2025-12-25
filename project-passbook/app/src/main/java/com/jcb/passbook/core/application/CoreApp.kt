package com.jcb.passbook.core.application

import android.app.Application
import com.jcb.passbook.BuildConfig
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.jcb.passbook.security.detection.SecurityManager
import com.jcb.passbook.utils.logging.RestrictiveReleaseTree
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

@HiltAndroidApp
class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ FIXED: Initialize Timber first before any logging
        initializeTimber()

        Timber.d("═══════════════════════════════════════")
        Timber.d("PassBook Application Initializing...")
        Timber.d("═══════════════════════════════════════")

        // ✅ FIXED: Wrap everything in try-catch to prevent app crashes
        try {
            initializeDependencies()
        } catch (e: Exception) {
            Timber.e(e, "❌ CRITICAL: Failed to initialize CoreApp dependencies")
            // Don't crash - allow app to start in degraded mode
        }

        Timber.d("✅ PassBook Application Started Successfully")
    }

    /**
     * ✅ FIXED: Separate Timber initialization with proper error handling
     */
    private fun initializeTimber() {
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            } else {
                Timber.plant(RestrictiveReleaseTree())
            }
        } catch (e: Exception) {
            // If Timber fails, print to System.err as fallback
            System.err.println("Failed to initialize Timber: ${e.message}")
        }
    }

    /**
     * ✅ FIXED: Initialize dependencies with proper exception handling
     */
    private fun initializeDependencies() {
        try {
            Timber.d("🔧 Initializing Hilt dependencies...")

            // Access dependencies via EntryPoint with timeout protection
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                CoreAppEntryPoint::class.java
            )

            // ✅ Get AuditLogger
            val auditLogger = try {
                entryPoint.auditLogger().also {
                    Timber.d("✅ AuditLogger initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize AuditLogger")
                null
            }

            // ✅ Get SecurityAuditManager
            val securityAuditManager = try {
                entryPoint.securityAuditManager().also {
                    Timber.d("✅ SecurityAuditManager initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize SecurityAuditManager")
                null
            }

            // ✅ FIXED: Initialize SecurityManager static auditing
            if (auditLogger != null) {
                try {
                    SecurityManager.initializeAuditing(auditLogger)
                    Timber.d("✅ SecurityManager auditing initialized")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to initialize SecurityManager auditing")
                }
            }

            // ✅ Start security monitoring if available
            if (securityAuditManager != null) {
                try {
                    securityAuditManager.startSecurityMonitoring()
                    Timber.d("✅ Security monitoring started")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to start security monitoring")
                }
            }

            // ✅ Log application startup
            if (auditLogger != null) {
                try {
                    auditLogger.logUserAction(
                        userId = null,
                        username = "SYSTEM",
                        eventType = AuditEventType.SYSTEM_EVENT,
                        action = "Application started (${BuildConfig.VERSION_NAME})",
                        resourceType = "SYSTEM",
                        resourceId = "APP",
                        outcome = AuditOutcome.SUCCESS,
                        errorMessage = null,
                        securityLevel = "NORMAL"
                    )
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to log startup event")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "❌ Dependency initialization failed")
            throw e // Re-throw to be caught by onCreate's outer try-catch
        }
    }

    override fun onTerminate() {
        Timber.d("🛑 PassBook Application Terminating...")

        // ✅ FIXED: Clean up SecurityManager on app termination
        try {
            SecurityManager.shutdown()
        } catch (e: Exception) {
            Timber.e(e, "Error during SecurityManager shutdown")
        }

        super.onTerminate()
    }

    /**
     * ✅ EntryPoint for accessing Hilt dependencies in Application class
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoreAppEntryPoint {
        fun auditLogger(): AuditLogger
        fun securityAuditManager(): SecurityAuditManager
    }
}
