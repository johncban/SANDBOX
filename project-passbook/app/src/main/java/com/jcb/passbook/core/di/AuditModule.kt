package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditQueue
import com.jcb.passbook.security.audit.MasterAuditLogger
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.data.local.database.dao.AuditDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuditModule {

    /**
     * ✅ FIXED: Provides AuditLogger with proper dependency injection
     * All dependencies are injected by Hilt, not called directly
     */
    @Provides
    @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        auditChainManager: AuditChainManager,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterAuditLogger {
        return MasterAuditLogger(
            context = context,
            auditJournalManager = auditJournalManager,
            auditChainManager = auditChainManager,
            sessionManager = sessionManager,
            secureMemoryUtils = secureMemoryUtils
        )
    }

    /**
     * ✅ FIXED: Provides lazy AuditLogger provider using kotlin.jvm.functions.Function0
     */
    @Provides
    @Singleton
    fun provideAuditLoggerProvider(
        auditLogger: MasterAuditLogger
    ): () -> MasterAuditLogger {
        return { auditLogger }
    }

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
    ): AuditChainManager {
        return AuditChainManager(context, auditDao)
    }

    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager {
        return AuditJournalManager(
            context = context,
            sessionManager = sessionManager,
            secureMemoryUtils = secureMemoryUtils
        )
    }

    /**
     * Lazy providers for deferred instantiation
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
}
