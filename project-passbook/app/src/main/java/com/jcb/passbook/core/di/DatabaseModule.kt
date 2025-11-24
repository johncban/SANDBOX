package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.AuditQueue
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.crypto.MasterKeyManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

/**
 * DatabaseModule - Dependency injection module for database and security components
 *
 * ✅ FIXED VERSION - Removed .fallbackToDestructiveMigration() for production safety
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

    @Provides @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        auditLogger: dagger.Lazy<AuditLogger>,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterKeyManager = MasterKeyManager(
        context = context,
        auditLoggerProvider = { auditLogger.get() },
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides @Singleton
    fun provideSessionManager(
        masterKeyManager: MasterKeyManager,
        auditLogger: dagger.Lazy<AuditLogger>,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager = SessionManager(
        masterKeyManager = masterKeyManager,
        auditLoggerProvider = { auditLogger.get() },
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager = AuditJournalManager(
        context = context,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager = DatabaseKeyManager(
        context = context,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    /**
     * Provides the encrypted Room database instance
     *
     * ✅ CRITICAL FIX: Removed .fallbackToDestructiveMigration()
     * This method would delete all user data on migration failure - unacceptable for a password manager
     *
     * If migration fails, the app should:
     * 1. Display an error to the user
     * 2. Offer data export/backup options
     * 3. Guide user through recovery process
     * 4. Never silently delete data
     */
    @Provides @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        Timber.d("Getting or creating database passphrase")
        val passphrase = runBlocking {
            databaseKeyManager.getOrCreateDatabasePassphrase()
        } ?: throw IllegalStateException("Failed to get database passphrase")

        @Suppress("DEPRECATION")
        val factory = SupportFactory(passphrase, null, false)

        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "passbook_database"
        )
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            // ✅ REMOVED: .fallbackToDestructiveMigration()
            // Production safety: Never delete user data automatically
            // Instead, migrations must be complete and tested
            .build()
    }

    @Provides @Singleton
    fun provideAuditDao(database: AppDatabase): AuditDao = database.auditDao()

    @Provides @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides @Singleton
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides @Singleton
    fun provideAuditChainManager(
        auditDao: AuditDao
    ): AuditChainManager = AuditChainManager(auditDao)

    @Provides @Singleton
    fun provideAuditQueue(
        auditDao: AuditDao,
        auditJournalManager: AuditJournalManager
    ): AuditQueue = AuditQueue(auditDao, auditJournalManager)

    @Provides @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        auditChainManager: dagger.Lazy<AuditChainManager>,
        auditQueue: dagger.Lazy<AuditQueue>
    ): AuditLogger = AuditLogger(
        auditQueueProvider = { auditQueue.get() },
        auditChainManagerProvider = { auditChainManager.get() },
        context = context
    )
}
