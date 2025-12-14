package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.AuditQueue
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.crypto.MasterKeyManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        auditDao: AuditDao
    ): AuditJournalManager {
        return AuditJournalManager(context, auditDao)
    }

    @Provides
    @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        auditJournalManager: AuditJournalManager
    ): AuditQueue {
        return AuditQueue(auditDao, auditJournalManager)
    }

    @Provides
    @Singleton
    fun provideAuditQueueProvider(auditQueue: dagger.Lazy<AuditQueue>): () -> AuditQueue {
        return { auditQueue.get() }
    }

    @Provides
    @Singleton
    fun provideAuditChainManager(auditDao: AuditDao): AuditChainManager {
        return AuditChainManager(auditDao)
    }

    @Provides
    @Singleton
    fun provideAuditChainManagerProvider(
        auditChainManager: dagger.Lazy<AuditChainManager>
    ): () -> AuditChainManager {
        return { auditChainManager.get() }
    }

    @Provides
    @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        auditQueueProvider: () -> AuditQueue,
        auditChainManagerProvider: () -> AuditChainManager,
        @ApplicationScope applicationScope: CoroutineScope
    ): AuditLogger {
        return AuditLogger(
            context,
            auditQueueProvider,
            auditChainManagerProvider,
            applicationScope
        )
    }

    @Provides
    @Singleton
    fun provideAuditLoggerProvider(
        auditLogger: dagger.Lazy<AuditLogger>
    ): () -> AuditLogger {
        return { auditLogger.get() }
    }

    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        auditLoggerProvider: () -> AuditLogger,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterKeyManager {
        return MasterKeyManager(context, auditLoggerProvider, secureMemoryUtils)
    }

    // ✅ FIXED: SessionManager constructor matches actual class signature
    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideSessionManager(
        masterKeyManager: MasterKeyManager,
        auditLoggerProvider: () -> AuditLogger,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager {
        return SessionManager(masterKeyManager, auditLoggerProvider, secureMemoryUtils)
    }

    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context, sessionManager, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLoggerProvider: () -> AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager {
        return SecurityAuditManager(auditLoggerProvider, auditDao, context)
    }
}
