// File: app/src/main/java/com/jcb/passbook/core/di/DatabaseModule.kt
package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.CategoryDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import android.util.Log
import javax.inject.Singleton

/**
 * DatabaseModule - Single source of truth for database dependencies
 *
 * ✅ FIXED: Changed getOrCreatePassphrase() to getPassphrase()
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "passbook_encrypted.db"
    private const val TAG = "DatabaseModule"

    /**
     * Provides the encrypted Room database instance
     * FIXED: Using getPassphrase() instead of getOrCreatePassphrase()
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseManager: KeystorePassphraseManager
    ): AppDatabase {
        Log.i(TAG, "🔧 Initializing encrypted database...")

        // ✅ FIXED: Changed from getOrCreatePassphrase() to getPassphrase()
        val passphrase = passphraseManager.getPassphrase()
            ?: throw IllegalStateException("Failed to retrieve database passphrase")

        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3
            )
            .fallbackToDestructiveMigration()
            .build()
            .also {
                Log.i(TAG, "✅ Database initialized successfully with SQLCipher encryption")
            }
    }

    /**
     * Provides ItemDao for password vault operations
     */
    @Provides
    @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao {
        Log.d(TAG, "📦 Providing ItemDao")
        return database.itemDao()
    }

    /**
     * Provides UserDao for user management
     */
    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        Log.d(TAG, "👤 Providing UserDao")
        return database.userDao()
    }

    /**
     * Provides CategoryDao for category management
     */
    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        Log.d(TAG, "📁 Providing CategoryDao")
        return database.categoryDao()
    }

    /**
     * Provides AuditDao for security audit logging
     */
    @Provides
    @Singleton
    fun provideAuditDao(database: AppDatabase): AuditDao {
        Log.d(TAG, "🔒 Providing AuditDao")
        return database.auditDao()
    }
}
