package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.dao.CategoryDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.AuditQueue
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.crypto.CryptoManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ========== COROUTINE SCOPE ==========
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // ========== CRYPTOGRAPHY PROVIDERS ==========
    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt {
        return Argon2Kt()
    }

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
    }

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context
    ): MasterKeyManager {
        return MasterKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager()
    }

    // ========== DATABASE ==========
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager
    ): AppDatabase {
        return runBlocking {
            try {
                val database = AppDatabase.createWithKeyManager(context, databaseKeyManager)
                database ?: throw IllegalStateException("Failed to initialize encrypted database")
            } catch (e: Exception) {
                Timber.e(e, "Critical: Database initialization failed")
                throw e
            }
        }
    }

    // ========== DAOs ==========
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideItemDao(database: AppDatabase): ItemDao {
        return database.itemDao()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideAuditDao(database: AppDatabase): AuditDao {
        return database.auditDao()
    }

    @Provides
    fun provideAuditMetadataDao(database: AppDatabase): AuditMetadataDao {
        return database.auditMetadataDao()
    }

    // ========== AUDIT SYSTEM ==========
    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context
    ): AuditJournalManager {
        return AuditJournalManager(context)
    }

    // ✅ FIX: AuditQueue - needs auditDao
    @Provides
    @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        auditJournalManager: AuditJournalManager
    ): AuditQueue {
        return AuditQueue(auditDao, auditJournalManager)
    }

    // ✅ FIX: AuditChainManager - only takes auditDao
    @Provides
    @Singleton
    fun provideAuditChainManager(
        auditDao: AuditDao
    ): AuditChainManager {
        return AuditChainManager(auditDao)
    }

    // ✅ CRITICAL FIX: AuditLogger constructor - add secureMemoryUtils parameter
    @Provides
    @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        auditQueueProvider: () -> AuditQueue,  // ✅ Provider function
        auditChainManagerProvider: () -> AuditChainManager,  // ✅ Provider function
        applicationScope: CoroutineScope,  // ✅ Direct parameter
        secureMemoryUtils: SecureMemoryUtils  // ✅ CRITICAL: Direct parameter
    ): AuditLogger {
        return AuditLogger(
            context = context,
            auditQueueProvider = auditQueueProvider,  // ✅ Pass provider directly
            auditChainManagerProvider = auditChainManagerProvider,  // ✅ Pass provider directly
            applicationScope = applicationScope,  // ✅ Direct parameter
            secureMemoryUtils = secureMemoryUtils  // ✅ CRITICAL: Pass direct
        )
    }

    // ✅ CRITICAL FIX: SessionManager constructor - add secureMemoryUtils parameter
    @Provides
    @Singleton
    fun provideSessionManager(
        masterKeyManager: MasterKeyManager,
        auditLoggerProvider: () -> AuditLogger,  // ✅ Provider function
        secureMemoryUtils: SecureMemoryUtils  // ✅ CRITICAL: Direct parameter
    ): SessionManager {
        return SessionManager(
            masterKeyManager = masterKeyManager,
            auditLoggerProvider = auditLoggerProvider,  // ✅ Pass provider directly
            secureMemoryUtils = secureMemoryUtils  // ✅ CRITICAL: Pass direct
        )
    }

    // ========== REPOSITORIES ==========
    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository {
        return UserRepository(userDao)
    }

    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository {
        return ItemRepository(itemDao)
    }

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository {
        return AuditRepository(auditDao)
    }
}
