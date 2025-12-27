package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditLogger
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
import kotlin.jvm.functions.Function0

@Module
@InstallIn(SingletonComponent::class)
object AuditModule {

    /**
     * ✅ FIXED: Returns AuditLogger interface (casts MasterAuditLogger)
     */
    @Provides
    @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        auditChainManager: AuditChainManager,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditLogger {
        return MasterAuditLogger(
            context = context,
            auditJournalManager = auditJournalManager,
            auditChainManager = auditChainManager,
            sessionManager = sessionManager,
            secureMemoryUtils = secureMemoryUtils
        ) as AuditLogger
    }

    /**
     * ✅ CRITICAL FIX: Must return Function0<AuditLogger> NOT () -> MasterAuditLogger
     * SecurityAuditManager expects: Function0<AuditLogger>
     */
    @Provides
    @Singleton
    fun provideAuditLoggerProvider(
        auditLogger: AuditLogger
    ): Function0<AuditLogger> {
        return object : Function0<AuditLogger> {
            override fun invoke(): AuditLogger = auditLogger
        }
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
}
