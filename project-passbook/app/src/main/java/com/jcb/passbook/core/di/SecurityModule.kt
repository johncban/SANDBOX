package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
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
 * SecurityModule provides all security-related dependencies.
 * FIXED: Correct parameter ordering for constructors
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
    }

    @Provides
    @Singleton
    fun provideBiometricEnrollmentMonitor(
        @ApplicationContext context: Context,
        auditLogger: Lazy<AuditLogger>
    ): BiometricEnrollmentMonitor {
        return BiometricEnrollmentMonitor(context, { auditLogger.get() })
    }

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        auditLogger: Lazy<AuditLogger>,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterKeyManager {
        // ✅ CORRECT ORDER: context, auditLoggerProvider, secureMemoryUtils
        return MasterKeyManager(context, { auditLogger.get() }, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        masterKeyManager: MasterKeyManager,
        auditLogger: Lazy<AuditLogger>,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager {
        // ✅ CORRECT ORDER: masterKeyManager, auditLoggerProvider, secureMemoryUtils
        return SessionManager(masterKeyManager, { auditLogger.get() }, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager {
        // ✅ FIXED: Removed auditLogger parameter entirely
        return DatabaseKeyManager(context, sessionManager, secureMemoryUtils)
    }


    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager {
        return AuditJournalManager(context, sessionManager, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideAuditChainManager(
        auditDao: AuditDao,
        auditMetadataDao: AuditMetadataDao,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditChainManager {
        return AuditChainManager(auditDao)
    }

    @Provides
    @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        journalManager: AuditJournalManager
    ): AuditQueue {
        return AuditQueue(auditDao, journalManager)
    }

    @Provides
    @Singleton
    fun provideAuditLogger(
        auditQueue: AuditQueue,
        auditChainManager: AuditChainManager,
        @ApplicationContext context: Context
    ): AuditLogger {
        return AuditLogger(auditQueue, auditChainManager, context)
    }

    @Provides
    @Singleton
    fun provideAuditVerificationService(
        auditDao: AuditDao,
        auditChainManager: AuditChainManager,
        auditLogger: Lazy<AuditLogger>
    ): AuditVerificationService {
        return AuditVerificationService(auditDao, auditChainManager, { auditLogger.get() })
    }

    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: Lazy<AuditLogger>,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager {
        return SecurityAuditManager({ auditLogger.get() }, auditDao, context)
    }

    @Provides
    @Singleton
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
