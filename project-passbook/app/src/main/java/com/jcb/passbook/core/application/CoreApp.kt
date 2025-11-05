package com.jcb.passbook.core.application

import android.app.Application
import com.jcb.passbook.BuildConfig // Fixed: Use correct BuildConfig import
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.jcb.passbook.utils.logging.RestrictiveReleaseTree
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class CoreApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Fixed: Proper Timber configuration
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(RestrictiveReleaseTree())
        }

        try {
            // Access dependencies via EntryPoint
            val entryPoint = EntryPointAccessors.fromApplication(this, CoreAppEntryPoint::class.java)
            val auditLogger = entryPoint.auditLogger()
            val securityAuditManager = entryPoint.securityAuditManager()

            // Start security monitoring
            securityAuditManager.startSecurityMonitoring()

            // Log application startup
            auditLogger.logUserAction(
                userId = null,
                username = "SYSTEM",
                eventType = AuditEventType.SYSTEM_EVENT,
                action = "Application started",
                resourceType = "SYSTEM",
                resourceId = "APP",
                outcome = AuditOutcome.SUCCESS,
                errorMessage = null,
                securityLevel = "NORMAL"
            )
        } catch (e: Exception) {
            // Fallback logging if DI fails
            Timber.e(e, "Failed to initialize CoreApp dependencies")
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CoreAppEntryPoint {
    fun auditLogger(): AuditLogger
    fun securityAuditManager(): SecurityAuditManager
}