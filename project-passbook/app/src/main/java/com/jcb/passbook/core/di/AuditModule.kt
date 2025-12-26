package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.audit.AuditChainManager
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
import kotlin.jvm.functions.Function0
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuditModule {

    @Provides
    @Singleton
    fun provideAuditQueue(
        @ApplicationContext context: Context,
        sessionManager: SessionManager
    ): AuditQueue = AuditQueue(context, sessionManager)

    @Provides
    @Singleton
    fun provideAuditChainManager(): AuditChainManager = AuditChainManager()

    @Provides
    @Singleton
    fun provideMasterAuditLogger(
        auditDao: AuditDao,
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterAuditLogger = MasterAuditLogger(
        auditDao,
        context,
        sessionManager,
        secureMemoryUtils
    )

    @Provides
    @Singleton
    fun provideAuditQueueProvider(
        auditQueue: AuditQueue
    ): kotlin.jvm.functions.Function0<AuditQueue> = { auditQueue }

    @Provides
    @Singleton
    fun provideAuditChainManagerProvider(
        auditChainManager: AuditChainManager
    ): kotlin.jvm.functions.Function0<AuditChainManager> = { auditChainManager }

    @Provides
    @Singleton
    fun provideMasterAuditLoggerProvider(
        masterAuditLogger: MasterAuditLogger
    ): kotlin.jvm.functions.Function0<MasterAuditLogger> = { masterAuditLogger }
}
