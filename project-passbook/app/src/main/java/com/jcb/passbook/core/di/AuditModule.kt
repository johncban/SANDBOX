package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditQueue
import com.jcb.passbook.security.audit.MasterAuditLogger
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.data.local.database.dao.AuditDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AuditModule - Provides ONLY audit-related dependencies
 *
 * REFACTORED:
 * ✅ Fixed missing closing parenthesis on provideAuditQueue()
 * ✅ Fixed missing closing brace on provideAuditQueue()
 * ✅ All audit providers centralized here
 * ✅ Lazy-loading providers for deferred injection
 *
 * DO NOT add crypto/security providers here!
 */
@Module
@InstallIn(SingletonComponent::class)
object AuditModule {

    @Provides
    @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        @ApplicationContext context: Context,
        sessionManager: SessionManager
    ): AuditQueue {
        return AuditQueue(
            auditDao = auditDao,
            context = context,
            sessionManager = sessionManager
        )
    }

    @Provides
    @Singleton
    fun provideAuditChainManager(
        @ApplicationContext context: Context,
        auditDao: AuditDao
    ): AuditChainManager = AuditChainManager(context, auditDao)

    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager = AuditJournalManager(
        context = context,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides
    @Singleton
    fun provideMasterAuditLogger(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        auditChainManager: AuditChainManager,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterAuditLogger = MasterAuditLogger(
        context = context,
        auditJournalManager = auditJournalManager,
        auditChainManager = auditChainManager,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    /**
     * Lazy providers for deferred instantiation
     * Used when components need on-demand access to dependencies
     */
    @Provides
    @Singleton
    fun provideAuditQueueProvider(
        auditQueue: AuditQueue
    ): () -> AuditQueue = { auditQueue }

    @Provides
    @Singleton
    fun provideAuditChainManagerProvider(
        auditChainManager: AuditChainManager
    ): () -> AuditChainManager = { auditChainManager }

    @Provides
    @Singleton
    fun provideMasterAuditLoggerProvider(
        masterAuditLogger: MasterAuditLogger
    ): () -> MasterAuditLogger = { masterAuditLogger }
}
