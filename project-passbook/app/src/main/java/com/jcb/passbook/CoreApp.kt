package com.jcb.passbook

import android.app.Application
import com.jcb.passbook.data.local.entities.AuditEventType
import com.jcb.passbook.data.local.entities.AuditOutcome
import com.jcb.passbook.util.audit.AuditLogger
import com.jcb.passbook.util.audit.SecurityAuditManager
import com.jcb.passbook.util.security.RestrictiveReleaseTree
import com.lambdapioneer.argon2kt.BuildConfig
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import dagger.hilt.components.SingletonComponent


/***    ------------------------------------------ STOP DELETION ------------------------------------------
@HiltAndroidApp
class CoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
        ------------------------------------------ STOP DELETION ------------------------------------------     ***/

// Update CoreApp.kt
@HiltAndroidApp
class CoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(RestrictiveReleaseTree())
        }

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
            securityLevel = "NORMAL"
        )
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(SingletonComponent::class)

interface CoreAppEntryPoint {
    fun auditLogger(): AuditLogger
    fun securityAuditManager(): SecurityAuditManager
}