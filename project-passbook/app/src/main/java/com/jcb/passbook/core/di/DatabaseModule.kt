package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.audit.AuditLogger
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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context
    ): MasterKeyManager = MasterKeyManager(context)

    @Provides
    @Singleton
    fun provideAuditLogger(): AuditLogger = AuditLogger()

    @Provides
    @Singleton
    fun provideSessionManager(
        masterKeyManager: MasterKeyManager,
        auditLogger: AuditLogger,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager = SessionManager(
        masterKeyManager = masterKeyManager,
        auditLoggerProvider = { auditLogger },  // ✅ Pass as lambda
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager = DatabaseKeyManager(
        context = context,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager
    ): AppDatabase {
        return try {
            System.loadLibrary("sqlcipher")
            Timber.d("Getting or creating database passphrase")
            val passphrase = runBlocking {
                databaseKeyManager.getOrCreateDatabasePassphrase()
            } ?: throw IllegalStateException("Failed to get database passphrase")

            @Suppress("DEPRECATION")
            val factory = SupportFactory(passphrase, null, false)
            val database = Room.databaseBuilder(
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
                .fallbackToDestructiveMigration()
                .build()

            Timber.i("Database initialized successfully")
            database
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize database")
            throw RuntimeException("Database initialization failed: ${e.message}", e)
        }
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides
    @Singleton
    fun provideAuditDao(database: AppDatabase): AuditDao = database.auditDao()
}
