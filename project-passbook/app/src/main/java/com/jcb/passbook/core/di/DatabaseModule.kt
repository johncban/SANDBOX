package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.*
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
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
 * DatabaseModule provides secure database configuration with SQLCipher encryption.
 * This module handles the critical security requirement of encrypted local storage.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the main application database with SQLCipher encryption.
     * The database key is managed by DatabaseKeyManager and is never persisted.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        dbKeyManager: DatabaseKeyManager,
        auditLogger: AuditLogger
    ): AppDatabase {
        return try {
            // Initialize SQLCipher native libraries
            SQLiteDatabase.loadLibs(context)
            
            // Get database encryption key (never persisted)
            val passphrase: ByteArray = dbKeyManager.getSqlCipherKey()
            
            // Create SQLCipher support factory
            val factory = SupportFactory(passphrase)
            
            // Zeroize the passphrase immediately after factory creation
            // SupportFactory clones the key internally, so this is safe
            java.util.Arrays.fill(passphrase, 0.toByte())
            
            // Build the encrypted database
            val database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "passbook_encrypted.db"
            )
                .openHelperFactory(factory)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // More secure than WAL
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(DatabaseCallback(auditLogger))
                .build()
            
            // Log successful database initialization
            auditLogger.logUserAction(
                userId = null,
                username = "SYSTEM",
                eventType = AuditEventType.SYSTEM_EVENT,
                action = "Database initialized with SQLCipher encryption",
                resourceType = "DATABASE",
                resourceId = "passbook_encrypted.db",
                outcome = AuditOutcome.SUCCESS,
                errorMessage = null,
                securityLevel = "HIGH"
            )
            
            database
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize encrypted database")
            
            // Log database initialization failure
            auditLogger.logUserAction(
                userId = null,
                username = "SYSTEM", 
                eventType = AuditEventType.SYSTEM_EVENT,
                action = "Database initialization failed",
                resourceType = "DATABASE",
                resourceId = "passbook_encrypted.db",
                outcome = AuditOutcome.FAILURE,
                errorMessage = e.message,
                securityLevel = "CRITICAL"
            )
            
            throw SecurityException("Failed to initialize secure database", e)
        }
    }

    /**
     * Database callback for lifecycle events and security monitoring.
     */
    private class DatabaseCallback(
        private val auditLogger: AuditLogger
    ) : RoomDatabase.Callback() {
        
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onCreate(db)
            
            // Log database creation
            auditLogger.logUserAction(
                userId = null,
                username = "SYSTEM",
                eventType = AuditEventType.DATA_ACCESS,
                action = "Database created",
                resourceType = "DATABASE",
                resourceId = "passbook_encrypted.db",
                outcome = AuditOutcome.SUCCESS,
                errorMessage = null,
                securityLevel = "HIGH"
            )
            
            Timber.i("Encrypted database created successfully")
        }
        
        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            super.onOpen(db)
            
            // Verify SQLCipher is working by attempting to read from sqlite_master
            try {
                db.query("SELECT name FROM sqlite_master WHERE type='table' LIMIT 1")
                
                auditLogger.logUserAction(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.DATA_ACCESS,
                    action = "Database opened and verified",
                    resourceType = "DATABASE",
                    resourceId = "passbook_encrypted.db",
                    outcome = AuditOutcome.SUCCESS,
                    errorMessage = null,
                    securityLevel = "HIGH"
                )
                
                Timber.i("Database connection verified")
                
            } catch (e: Exception) {
                Timber.e(e, "Database verification failed")
                
                auditLogger.logUserAction(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.DATA_ACCESS,
                    action = "Database verification failed",
                    resourceType = "DATABASE",
                    resourceId = "passbook_encrypted.db",
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.message,
                    securityLevel = "CRITICAL"
                )
                
                throw SecurityException("Database verification failed", e)
            }
        }
    }

    // DAO Providers - these are injected automatically by Room
    
    @Provides
    fun providePasswordItemDao(database: AppDatabase): PasswordItemDao = 
        database.passwordItemDao()
    
    @Provides 
    fun provideUserDao(database: AppDatabase): UserDao = 
        database.userDao()
        
    @Provides
    fun provideAuditDao(database: AppDatabase): AuditDao = 
        database.auditDao()
        
    @Provides
    fun provideAuditMetadataDao(database: AppDatabase): AuditMetadataDao = 
        database.auditMetadataDao()
        
    @Provides
    fun provideBiometricKeyDao(database: AppDatabase): BiometricKeyDao = 
        database.biometricKeyDao()
        
    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = 
        database.sessionDao()
}