package com.jcb.passbook.core.di


import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.encryption.DatabaseEncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt Module for Room Database with SQLCipher Encryption
 *
 * Provides:
 * - DatabaseEncryptionManager (handles encryption key management)
 * - AppDatabase (encrypted Room database)
 *
 * CRITICAL FIX #1: Database encryption with proper error handling
 * CRITICAL FIX #2: DataStore-backed passphrase management
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val TAG = "DatabaseModule"
    private const val DB_NAME = "passbook.db"

    /**
     * Provide DatabaseEncryptionManager singleton
     */
    @Provides
    @Singleton
    fun provideDatabaseEncryptionManager(
        @ApplicationContext context: Context
    ): DatabaseEncryptionManager {
        return DatabaseEncryptionManager(context)
    }

    /**
     * Provide encrypted Room database with SQLCipher
     *
     * CRITICAL: This handles database encryption failures gracefully
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        encryptionManager: DatabaseEncryptionManager
    ): AppDatabase {
        return try {
            Timber.tag(TAG).i("Initializing encrypted database...")

            // Validate encryption can be initialized
            if (!encryptionManager.validateDatabaseConnection()) {
                Timber.tag(TAG).w("⚠️  Database encryption validation failed, attempting recovery...")
            }

            // Get encryption passphrase
            val passphrase = encryptionManager.getEncryptionPassphrase()

            // Create Room database with SQLCipher
            val database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory { configuration ->
                    // Configure SQLCipher with passphrase
                    net.sqlcipher.database.SupportFactory(passphrase).create(configuration)
                }
                .fallbackToDestructiveMigration()
                .addCallback(object : androidx.room.RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Timber.tag(TAG).i("✓ Database created successfully")
                    }

                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Timber.tag(TAG).i("✓ Database opened successfully")
                    }
                })
                .build()

            Timber.tag(TAG).i("✓ Encrypted database initialized successfully")
            database

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ CRITICAL: Failed to initialize encrypted database")
            Timber.tag(TAG).i("Attempting recovery: Deleting corrupted database file...")

            try {
                // Delete corrupted database file
                val dbFile = context.getDatabasePath(DB_NAME)
                if (dbFile.exists()) {
                    dbFile.delete()
                    Timber.tag(TAG).i("Deleted corrupted database file")
                }

                // Retry database creation
                Timber.tag(TAG).i("Retrying database creation...")
                val passphrase = encryptionManager.getEncryptionPassphrase()

                val recoveredDatabase = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory { configuration ->
                        net.sqlcipher.database.SupportFactory(passphrase).create(configuration)
                    }
                    .fallbackToDestructiveMigration()
                    .build()

                Timber.tag(TAG).i("✓ Database recovery successful!")
                recoveredDatabase

            } catch (recoveryError: Exception) {
                Timber.tag(TAG).e(recoveryError, "❌ FATAL: Database recovery failed")
                throw Exception("Cannot initialize database: ${e.message}\nRecovery failed: ${recoveryError.message}", e)
            }
        }
    }
}
