package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.AuditQueue
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule provides application-level dependencies.
 *
 * FIXED: All constructor signatures now match actual class definitions
 * Database provisioning is handled by DatabaseModule.kt
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ============================================================================================
    // CRYPTOGRAPHY PROVIDERS
    // ============================================================================================

    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    /**
     * FIXED: Added SecureMemoryUtils provider
     * Required by: AuditChainManager, AuditJournalManager
     */
    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

    // ============================================================================================
    // REPOSITORY PROVIDERS
    // ============================================================================================

    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository =
        ItemRepository(itemDao)

    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository =
        UserRepository(userDao)

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository =
        AuditRepository(auditDao)

    // ============================================================================================
    // SECURITY AUDIT PROVIDERS
    // ============================================================================================

    /**
     * FIXED: AuditChainManager constructor requires:
     * - auditDao: AuditDao
     * - auditMetadataDao: AuditMetadataDao
     * - secureMemoryUtils: SecureMemoryUtils
     */
    @Provides
    @Singleton
    fun provideAuditChainManager(
        auditDao: AuditDao,
        auditMetadataDao: AuditMetadataDao,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditChainManager = AuditChainManager(auditDao, auditMetadataDao, secureMemoryUtils)

    /**
     * FIXED: AuditJournalManager constructor requires:
     * - context: Context
     * - sessionManager: SessionManager
     * - secureMemoryUtils: SecureMemoryUtils
     */
    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager = AuditJournalManager(context, sessionManager, secureMemoryUtils)

    /**
     * FIXED: AuditQueue constructor requires:
     * - auditDao: AuditDao
     * - journalManager: AuditJournalManager
     */
    @Provides
    @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        journalManager: AuditJournalManager
    ): AuditQueue = AuditQueue(auditDao, journalManager)

    /**
     * FIXED: AuditLogger constructor requires:
     * - auditQueue: AuditQueue
     * - auditChainManager: AuditChainManager
     * - context: Context
     */
    @Provides
    @Singleton
    fun provideAuditLogger(
        auditQueue: AuditQueue,
        auditChainManager: AuditChainManager,
        @ApplicationContext context: Context
    ): AuditLogger = AuditLogger(auditQueue, auditChainManager, context)

    /**
     * FIXED: SecurityAuditManager constructor requires:
     * - auditLogger: AuditLogger
     * - auditDao: AuditDao
     * - context: Context
     */
    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager = SecurityAuditManager(auditLogger, auditDao, context)
}