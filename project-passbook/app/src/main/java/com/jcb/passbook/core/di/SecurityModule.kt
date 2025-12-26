package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.security.audit.*
import com.jcb.passbook.security.crypto.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton
import com.jcb.passbook.security.audit.MasterAuditLogger
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils



/**
 * ✅ REFACTORED: Complete DI configuration for PassBook security
 * 
 * TIER STRUCTURE:
 * Tier 1 (Leaf): SecureMemoryUtils, AuditLogger
 * Tier 2 (Crypto): CryptoManager, SessionManager, PasswordEncryptionService
 * Tier 3 (Audit): AuditJournalManager, AuditChainManager
 * Tier 4 (DB): AppDatabase, MasterAuditLogger
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    // ========== TIER 1: Foundational Services (No Dependencies) ==========

    /**
     * Secure memory utilities for sensitive data handling
     */
    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
    }

    /**
     * Audit logger for security logging
     */
    @Provides
    @Singleton
    fun provideAuditLogger(): AuditLogger {
        return AuditLogger()
    }

    /**
     * Keystore manager for passphrase storage and retrieval
     */
    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager {
        return KeystorePassphraseManager(context)
    }

    // ========== TIER 2: Crypto Services ==========

    /**
     * CryptoManager orchestrates all encryption/decryption operations
     * DEPENDENCY: SecureMemoryUtils (for secure memory handling)
     * CRITICAL: Must be available BEFORE SessionManager
     */
    @Provides
    @Singleton
    fun provideCryptoManager(
        secureMemoryUtils: SecureMemoryUtils
    ): CryptoManager {
        val manager = CryptoManager(secureMemoryUtils)
        manager.initializeKeystore()
        return manager
    }

    /**
     * Session manager for AMK lifecycle and session management
     * DEPENDENCIES: 
     * - context (for activity-based auth)
     * - keystoreManager (for passphrase retrieval and derivation)
     * - cryptoManager (for crypto operations)
     * 
     * ✅ FIXED: Now accepts all required dependencies
     */
    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager,
        cryptoManager: CryptoManager
    ): SessionManager {
        return SessionManager(context, keystoreManager, cryptoManager)
    }

    /**
     * Password encryption service for end-to-end encryption
     * DEPENDENCY: CryptoManager
     */
    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        cryptoManager: CryptoManager
    ): PasswordEncryptionService {
        return PasswordEncryptionService(cryptoManager)
    }

    // ========== TIER 3: Audit & Persistence ==========

    /**
     * Audit queue supplier (lazy initialization)
     * Returns a function that creates AuditQueue on demand
     * Used when audit database is unavailable
     * 
     * ✅ FIXED: Returns correct type () -> AuditQueue
     */
    @Provides
    @Singleton
    fun provideAuditQueueSupplier(
        @ApplicationContext context: Context,
        sessionManager: SessionManager
    ): () -> AuditQueue {
        return {
            AuditQueue(context, sessionManager)
        }
    }

    /**
     * Audit journal manager for persistent audit logging
     * DEPENDENCIES: 
     * - context
     * - sessionManager (for ephemeral key generation)
     * - secureMemoryUtils (for secure operations)
     */
    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager {
        return AuditJournalManager(context, sessionManager, secureMemoryUtils)
    }

    /**
     * Audit chain manager for chained audit verification
     * DEPENDENCIES: 
     * - context
     * - auditJournalManager (for audit operations)
     * - sessionManager (for session metadata)
     * - database (for audit entry persistence)
     * 
     * ✅ FIXED: Now accepts all required dependencies
     */
    @Provides
    @Singleton
    fun provideAuditChainManager(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        sessionManager: SessionManager,
        database: AppDatabase
    ): AuditChainManager {
        return AuditChainManager(
            context = context,
            auditJournal = auditJournalManager,
            sessionManager = sessionManager,
            database = database
        )
    }

    /**
     * Master audit logger coordinating all audit operations
     * DEPENDENCIES: AuditJournalManager, AuditChainManager
     */
    @Provides
    @Singleton
    fun provideMasterAuditLogger(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        auditChainManager: AuditChainManager,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterAuditLogger {  // ✅ NOW RESOLVED
        return MasterAuditLogger(
            context,
            auditJournalManager,
            auditChainManager,
            sessionManager,
            secureMemoryUtils
        )
    }



    // ========== TIER 4: Database & Persistence ==========

    /**
     * PassBook encrypted database with SQLCipher
     * DEPENDENCIES: 
     * - context
     * - keystoreManager (for database passphrase)
     * 
     * CRITICAL: Passphrase initialization must happen synchronously during DI
     * The keystoreManager.getPassphrase() method handles:
     * - Retrieving existing passphrase from EncryptedSharedPreferences
     * - Generating new passphrase if not exists
     * - Returning passphrase without requiring FragmentActivity
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager
    ): AppDatabase {
        // Get or generate passphrase (must happen synchronously)
        val passphrase = keystoreManager.getPassphrase()
            ?: throw IllegalStateException(
                "Failed to initialize database passphrase. " +
                "Check KeystorePassphraseManager.getPassphrase() implementation."
            )

        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "passbook_encrypted.db"
        )
            .openHelperFactory(
                net.zetetic.database.sqlcipher.SupportFactory(passphrase)
            )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addCallback(object : Room.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    Timber.i("✅ PassBook database created and encrypted")
                }

                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    Timber.d("🔐 PassBook database opened successfully")
                }
            })
            .build()

        Timber.i("🗄️ Database initialized with encryption")
        return database
    }
}
