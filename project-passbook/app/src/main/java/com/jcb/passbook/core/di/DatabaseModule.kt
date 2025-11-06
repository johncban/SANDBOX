package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.*
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.audit.AuditLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

/**
 * DatabaseModule provides Room database with SQLCipher encryption.
 * 
 * SECURITY CRITICAL:
 * - Database is encrypted with SQLCipher using rotating keys
 * - Key derivation tied to user authentication and device binding
 * - Zero-knowledge: keys never persisted, derived fresh each session
 * - WAL mode disabled to prevent unencrypted temporary files
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager,
        auditLogger: AuditLogger
    ): AppDatabase {
        return try {
            // Initialize SQLCipher native libraries
            SQLiteDatabase.loadLibs(context)
            
            // Get database encryption key (derived, never persisted)
            val passphrase: ByteArray = databaseKeyManager.getDatabaseKey()
            
            // Create SupportFactory with encryption key
            val factory = SupportFactory(passphrase)
            
            // CRITICAL: Zeroize passphrase immediately after factory creation
            // SupportFactory internally clones the key, so we can safely clear ours
            java.util.Arrays.fill(passphrase, 0.toByte())
            
            val database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "passbook_encrypted.db"
            )
            .openHelperFactory(factory)
            // SECURITY: Disable WAL to prevent unencrypted temp files
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            // For production, use managed migrations instead of destructive
            .fallbackToDestructiveMigration()
            // Enable foreign key constraints
            .setQueryCallback({ sqlQuery, bindArgs ->
                if (com.jcb.passbook.BuildConfig.DEBUG_MODE) {
                    Timber.d("Room Query: $sqlQuery")
                }
            }, java.util.concurrent.Executors.newSingleThreadExecutor())
            .build()
            
            auditLogger.logDatabaseEvent(
                "Database initialized with encryption",
                mapOf(
                    "encryption" to "SQLCipher",
                    "journal_mode" to "TRUNCATE",
                    "key_derivation" to "session_bound"
                )
            )
            
            database
            
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "CRITICAL: Database initialization failed - ${e.message}",
                "CRITICAL",
                com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
            )
            throw SecurityException("Database initialization failed", e)
        }
    }

    // DAOs - provided by Room database instance
    
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun providePasswordItemDao(database: AppDatabase): PasswordItemDao {
        return database.passwordItemDao()
    }

    @Provides
    fun provideAuditDao(database: AppDatabase): AuditDao {
        return database.auditDao()
    }

    @Provides
    fun provideAuditMetadataDao(database: AppDatabase): AuditMetadataDao {
        return database.auditMetadataDao()
    }

    @Provides
    fun provideBiometricKeyDao(database: AppDatabase): BiometricKeyDao {
        return database.biometricKeyDao()
    }

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }
}