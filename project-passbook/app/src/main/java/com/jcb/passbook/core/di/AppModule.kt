package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.*
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

/**
 * ✅ FIXED: DatabaseModule with proper SQLCipher encryption and migration
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager
    ): AppDatabase {
        Timber.i("📦 Initializing encrypted database...")

        return try {
            // Get passphrase from keystore for SQLCipher encryption
            val passphrase = keystoreManager.getPassphrase()
                ?: throw SecurityException("Failed to retrieve database passphrase")

            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "passbook_vault.db"
            )
                // ✅ Enable SQLCipher encryption
                .openHelperFactory(SupportFactory(passphrase))
                // ✅ Add migration 1→2
                .addMigrations(AppDatabase.MIGRATION_1_2)
                // Enable Write-Ahead Logging for better concurrency
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // Enable multi-instance invalidation
                .enableMultiInstanceInvalidation()
                .build()
                .also {
                    Timber.i("✅ Database initialized successfully")
                }
        } catch (e: Exception) {
            Timber.e(e, "❌ Database initialization failed")
            throw RuntimeException("Failed to initialize database: ${e.message}", e)
        }
    }

    @Provides
    @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    @Provides
    @Singleton
    fun provideAuditDao(database: AppDatabase): AuditDao = database.auditDao()

    @Provides
    @Singleton
    fun provideAuditMetadataDao(database: AppDatabase): AuditMetadataDao = database.auditMetadataDao()

    @Provides
    @Singleton
    fun providePasswordCategoryDao(database: AppDatabase): PasswordCategoryDao = database.passwordCategoryDao()
}
