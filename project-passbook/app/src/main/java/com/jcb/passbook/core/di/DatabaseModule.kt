package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

private const val TAG = "DatabaseModule"

/**
 * ✅ CRITICAL FIX: Database initialization in application-scope singleton
 * - Prevents JobCancellationException on config changes
 * - Ensures DB is fully ready before exposing to activities
 * - Uses application context for proper lifecycle management
 * - Now uses AppDatabase migrations (v7) instead of local migrations
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * ✅ Application-scoped coroutine scope that survives activity lifecycle
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * ✅ CRITICAL: Database provider using AppDatabase.create() method
     *
     * This delegates all migration logic to AppDatabase.kt which handles:
     * - Migration 1→2: Add lastAccessedAt to items
     * - Migration 2→3: Add audit_entries table
     * - Migration 3→4: Add audit chaining and metadata
     * - Migration 4→5: Add categories table
     * - Migration 5→6: Add category foreign key to items
     * - Migration 6→7: Add type field to items
     *
     * Database is created at version 7 with all migrations applied automatically.
     */
    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager
    ): AppDatabase {
        Timber.tag(TAG).d("=== Initializing AppDatabase singleton ===")

        return try {
            // ✅ CRITICAL: Initialize key synchronously in singleton scope
            // This blocks DI graph creation but ensures DB is ready before first use
            val passphrase = runBlocking(Dispatchers.IO) {
                databaseKeyManager.getOrCreateDatabasePassphrase()
            }

            if (passphrase == null) {
                Timber.tag(TAG).e("FATAL: Failed to create database passphrase")
                throw IllegalStateException("Database passphrase initialization failed")
            }

            Timber.tag(TAG).i("✓ Database passphrase ready (${passphrase.size} bytes)")

            // ✅ Use AppDatabase.create() which handles all migrations internally
            val database = AppDatabase.create(context.applicationContext, passphrase)

            // ✅ Verify database is accessible before returning
            runBlocking(Dispatchers.IO) {
                try {
                    database.openHelper.writableDatabase
                    Timber.tag(TAG).i("✓ Database verified accessible")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Database verification failed")
                    throw e
                }
            }

            Timber.tag(TAG).d("=== AppDatabase singleton created successfully ===")
            database

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "CRITICAL: Failed to initialize database")
            throw IllegalStateException("Database initialization failed: ${e.message}", e)
        }
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao {
        return database.itemDao()
    }

    @Provides
    @Singleton
    fun provideAuditDao(database: AppDatabase): AuditDao {
        return database.auditDao()
    }
}
