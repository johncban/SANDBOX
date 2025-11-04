package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.security.audit.*
import com.jcb.passbook.security.crypto.*
import com.jcb.passbook.security.detection.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SecurityModule provides dependency injection for security-related components.
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
        auditLogger: AuditLogger
    ): BiometricEnrollmentMonitor {
        return BiometricEnrollmentMonitor(context, auditLogger)
    }

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        auditLogger: AuditLogger,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterKeyManager {
        return MasterKeyManager(context, auditLogger, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        masterKeyManager: MasterKeyManager,
        auditLogger: AuditLogger,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager {
        return SessionManager(masterKeyManager, auditLogger, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        auditLogger: AuditLogger,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context, sessionManager, auditLogger, secureMemoryUtils)
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
        return AuditChainManager(auditDao, auditMetadataDao, secureMemoryUtils)
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
    fun provideEnhancedAuditLogger(
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
        enhancedAuditLogger: AuditLogger
    ): AuditVerificationService {
        return AuditVerificationService(auditDao, auditChainManager, enhancedAuditLogger)
    }

    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager {
        return SecurityAuditManager(auditLogger, auditDao, context)
    }

    /**
     * Initialize security components that need setup
     */
    @Provides
    @Singleton
    fun provideSecurityInitializer(
        masterKeyManager: MasterKeyManager,
        sessionManager: SessionManager,
        biometricEnrollmentMonitor: BiometricEnrollmentMonitor,
        auditVerificationService: AuditVerificationService,
        securityAuditManager: SecurityAuditManager,
        enhancedAuditLogger: AuditLogger
    ): SecurityInitializer {
        return SecurityInitializer(
            masterKeyManager,
            sessionManager,
            biometricEnrollmentMonitor,
            auditVerificationService,
            securityAuditManager,
            enhancedAuditLogger
        )
    }
}

/**
 * SecurityInitializer coordinates the initialization of security components
 */
class SecurityInitializer(
    private val masterKeyManager: MasterKeyManager,
    private val sessionManager: SessionManager,
    private val biometricEnrollmentMonitor: BiometricEnrollmentMonitor,
    private val auditVerificationService: AuditVerificationService,
    private val securityAuditManager: SecurityAuditManager,
    private val enhancedAuditLogger: AuditLogger
) {

    suspend fun initialize(): Boolean {
        return try {
            // Initialize audit logging first
            SecurityManager.initializeAuditing(enhancedAuditLogger)

            // Initialize master key infrastructure
            masterKeyManager.initializeMasterKey()

            // Start monitoring services
            biometricEnrollmentMonitor.startMonitoring()
            auditVerificationService.startPeriodicVerification()
            securityAuditManager.startSecurityMonitoring()

            enhancedAuditLogger.logAppLifecycle(
                "Security system initialized",
                mapOf("components" to "all")
            )

            true
        } catch (e: Exception) {
            enhancedAuditLogger.logSecurityEvent(
                "Failed to initialize security system: ${e.message}",
                "CRITICAL",
                com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
            )
            false
        }
    }

    fun shutdown() {
        try {
            biometricEnrollmentMonitor.stopMonitoring()
            auditVerificationService.stopPeriodicVerification()
            securityAuditManager.stopSecurityMonitoring()

            enhancedAuditLogger.logAppLifecycle(
                "Security system shutdown",
                mapOf("reason" to "application_exit")
            )
        } catch (e: Exception) {
            // Best effort logging
            timber.log.Timber.e(e, "Error during security shutdown")
        }
    }
}