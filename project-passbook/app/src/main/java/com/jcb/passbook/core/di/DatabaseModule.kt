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
 * ✅ CRITICAL FIX: Proper initialization sequence to prevent re-initialization loop
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
     * ✅ CRITICAL FIX: Provides the encrypted Room database instance
     *
     * FIXES APPLIED:
     * 1. Synchronous initialization check BEFORE creating database
     * 2. Clear logging to track initialization state
     * 3. Fail-fast if passphrase cannot be retrieved
     * 4. Removed fallbackToDestructiveMigration for data safety
     *
     * This ensures database key is created ONCE and reused consistently
     */
    @Provides @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager
    ): AppDatabase {
        System.loadLibrary("sqlcipher")

        Timber.d("=== DatabaseModule: Initializing AppDatabase ===")

        // ✅ FIX: Check if database key already exists BEFORE attempting creation
        val isInitialized = databaseKeyManager.isDatabaseKeyInitialized()
        Timber.d("Database key initialized: $isInitialized")

        val passphrase = runBlocking {
            try {
                val key = databaseKeyManager.getOrCreateDatabasePassphrase()
                if (key == null) {
                    Timber.e("❌ CRITICAL: Failed to get database passphrase")
                    throw IllegalStateException("Cannot initialize database without passphrase")
                }

                Timber.i("✅ Database passphrase retrieved successfully (${key.size} bytes)")
                key

            } catch (e: Exception) {
                Timber.e(e, "❌ FATAL: Exception during database passphrase initialization")
                throw IllegalStateException("Database initialization failed", e)
            }
        }

        @Suppress("DEPRECATION")
        val factory = SupportFactory(passphrase, null, false)

        Timber.d("Building Room database with SQLCipher encryption")

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
            .build().also {
                Timber.i("✅ AppDatabase singleton created successfully")
            }
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
