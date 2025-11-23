package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.security.audit.*
import com.jcb.passbook.security.crypto.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.Lazy
import javax.inject.Singleton

/**
 * SecurityModule provides only UNIQUE security-related dependencies
 * that are NOT already provided in DatabaseModule
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    // ✅ FIXED: BiometricEnrollmentMonitor requires Context FIRST
    @Provides @Singleton
    fun provideBiometricEnrollmentMonitor(
        @ApplicationContext context: Context,
        auditLogger: Lazy<AuditLogger>
    ): BiometricEnrollmentMonitor {
        return BiometricEnrollmentMonitor(
            context = context,  // ✅ FIRST parameter
            auditLoggerProvider = { auditLogger.get() }  // ✅ SECOND parameter
        )
    }

    // ✅ KEEP: Unique to SecurityModule
    @Provides @Singleton
    fun provideAuditVerificationService(
        auditDao: AuditDao,
        auditChainManager: AuditChainManager,
        auditLogger: Lazy<AuditLogger>
    ): AuditVerificationService {
        return AuditVerificationService(auditDao, auditChainManager, { auditLogger.get() })
    }

    // ✅ FIXED: SecurityAuditManager requires auditLoggerProvider FIRST, then auditDao, then context
    @Provides @Singleton
    fun provideSecurityAuditManager(
        auditLogger: Lazy<AuditLogger>,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager {
        return SecurityAuditManager(
            auditLoggerProvider = { auditLogger.get() },  // ✅ FIRST parameter
            auditDao = auditDao,  // ✅ SECOND parameter
            context = context  // ✅ THIRD parameter
        )
    }

    // ✅ KEEP: Unique to SecurityModule
    @Provides @Singleton
    fun provideSecurityInitializer(
        masterKeyManager: MasterKeyManager,
        sessionManager: SessionManager,
        biometricEnrollmentMonitor: BiometricEnrollmentMonitor,
        auditVerificationService: AuditVerificationService,
        securityAuditManager: SecurityAuditManager,
        auditLogger: AuditLogger
    ): SecurityInitializer {
        return SecurityInitializer(
            masterKeyManager,
            sessionManager,
            biometricEnrollmentMonitor,
            auditVerificationService,
            securityAuditManager,
            auditLogger
        )
    }
}

class SecurityInitializer(
    private val masterKeyManager: MasterKeyManager,
    private val sessionManager: SessionManager,
    private val biometricEnrollmentMonitor: BiometricEnrollmentMonitor,
    private val auditVerificationService: AuditVerificationService,
    private val securityAuditManager: SecurityAuditManager,
    private val auditLogger: AuditLogger
) {
    suspend fun initialize(): Boolean {
        return try {
            masterKeyManager.initializeMasterKey()
            biometricEnrollmentMonitor.startMonitoring()
            auditVerificationService.startPeriodicVerification()
            securityAuditManager.startSecurityMonitoring()
            auditLogger.logAppLifecycle(
                "Security system initialized successfully",
                mapOf("components" to "all")
            )
            true
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Failed to initialize security system: ${e.message}",
                "CRITICAL",
                com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
            )
            false
        }
    }

    fun shutdown() {
        try {
            kotlinx.coroutines.runBlocking {
                if (sessionManager.isSessionActive()) {
                    sessionManager.endSession("Application shutdown")
                }
            }
            biometricEnrollmentMonitor.stopMonitoring()
            auditVerificationService.stopPeriodicVerification()
            securityAuditManager.stopSecurityMonitoring()
            auditLogger.logAppLifecycle(
                "Security system shutdown complete",
                mapOf("reason" to "application_exit")
            )
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Error during security shutdown")
        }
    }
}
