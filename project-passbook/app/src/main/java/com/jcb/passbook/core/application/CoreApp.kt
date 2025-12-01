package com.jcb.passbook.core.application

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import com.jcb.passbook.BuildConfig
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.jcb.passbook.security.detection.SecurityManager
import com.jcb.passbook.utils.logging.RestrictiveReleaseTree
import com.jcb.passbook.utils.memory.MemoryManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

@HiltAndroidApp
class CoreApp : Application() {

    private lateinit var memoryManager: MemoryManager

    override fun onCreate() {
        super.onCreate()

        // ✅ Initialize Timber first
        initializeTimber()

        Timber.d("═══════════════════════════════════════")
        Timber.d("PassBook Application Initializing...")
        Timber.d("═══════════════════════════════════════")

        try {
            initializeDependencies()
            registerLifecycleCallbacks()
            registerComponentCallbacks()
        } catch (e: Exception) {
            Timber.e(e, "❌ CRITICAL: Failed to initialize CoreApp")
        }

        Timber.d("✅ PassBook Application Started Successfully")
    }

    /**
     * Initialize Timber logging
     */
    private fun initializeTimber() {
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(Timber.DebugTree())
            } else {
                Timber.plant(RestrictiveReleaseTree())
            }
        } catch (e: Exception) {
            System.err.println("Failed to initialize Timber: ${e.message}")
        }
    }

    /**
     * Initialize Hilt dependencies
     */
    private fun initializeDependencies() {
        try {
            Timber.d("🔧 Initializing Hilt dependencies...")

            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                CoreAppEntryPoint::class.java
            )

            // Initialize MemoryManager
            memoryManager = try {
                entryPoint.memoryManager().also {
                    it.logMemoryStats()
                    Timber.d("✅ MemoryManager initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize MemoryManager")
                throw e
            }

            // Initialize AuditLogger
            val auditLogger = try {
                entryPoint.auditLogger().also {
                    Timber.d("✅ AuditLogger initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize AuditLogger")
                null
            }

            // Initialize SecurityAuditManager
            val securityAuditManager = try {
                entryPoint.securityAuditManager().also {
                    Timber.d("✅ SecurityAuditManager initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to initialize SecurityAuditManager")
                null
            }

            // Initialize SecurityManager static auditing
            if (auditLogger != null) {
                try {
                    SecurityManager.initializeAuditing(auditLogger)
                    Timber.d("✅ SecurityManager auditing initialized")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to initialize SecurityManager auditing")
                }
            }

            // Start security monitoring
            if (securityAuditManager != null) {
                try {
                    securityAuditManager.startSecurityMonitoring()
                    Timber.d("✅ Security monitoring started")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Failed to start security monitoring")
                }
            }

            // Log application startup
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
            throw e
        }
    }

    /**
     * ✅ CRITICAL FIX: Register Activity Lifecycle Callbacks
     */
    private fun registerLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Timber.d("Activity created: ${activity.localClassName}")
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {
                // Suggest GC when activity pauses
                memoryManager.requestGarbageCollection()
            }

            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                // Force GC when activity destroyed
                memoryManager.requestGarbageCollection()
                Timber.d("Activity destroyed: ${activity.localClassName}")
            }
        })
    }

    /**
     * ✅ CRITICAL FIX: Register Component Callbacks for Low Memory
     */
    private fun registerComponentCallbacks() {
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                Timber.w("onTrimMemory: level=$level")
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                    ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                        memoryManager.requestGarbageCollection()
                        memoryManager.logMemoryStats()
                    }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}

            override fun onLowMemory() {
                Timber.e("onLowMemory called - system critically low")
                memoryManager.requestGarbageCollection()
                memoryManager.logMemoryStats()
            }
        })
    }

    override fun onTerminate() {
        Timber.d("🛑 PassBook Application Terminating...")

        try {
            SecurityManager.shutdown()
        } catch (e: Exception) {
            Timber.e(e, "Error during SecurityManager shutdown")
        }

        super.onTerminate()
    }

    /**
     * EntryPoint for accessing Hilt dependencies
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoreAppEntryPoint {
        fun auditLogger(): AuditLogger
        fun securityAuditManager(): SecurityAuditManager
        fun memoryManager(): MemoryManager
    }
}
