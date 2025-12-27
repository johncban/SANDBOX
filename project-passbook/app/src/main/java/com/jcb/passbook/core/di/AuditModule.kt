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

@Module
@InstallIn(SingletonComponent::class)
object AuditModule {

    // ✅ FIXED: Added auditDao parameter and explicit named parameters
    @Provides
    @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        @ApplicationContext context: Context,
        sessionManager: SessionManager
    ): AuditQueue = AuditQueue(
        auditDao = auditDao,
        context = context,
        sessionManager = sessionManager
    )

    @Provides
    @Singleton
    fun provideAuditChainManager(): AuditChainManager = AuditChainManager()

    // ✅ NEW: Added missing AuditJournalManager provider
    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context
    ): AuditJournalManager = AuditJournalManager(context)

    // ✅ FIXED: Completely rewrote with correct parameters and order
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
