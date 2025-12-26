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

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
    }

    @Provides
    @Singleton
    fun provideAuditLogger(): AuditLogger {
        return AuditLogger()
    }

    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager {
        return KeystorePassphraseManager(context)
    }

    // ========== TIER 2: Crypto Services ==========

    @Provides
    @Singleton
    fun provideCryptoManager(
        secureMemoryUtils: SecureMemoryUtils
    ): CryptoManager {
        val manager = CryptoManager(secureMemoryUtils)
        manager.initializeKeystore()
        return manager
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager,
        cryptoManager: CryptoManager
    ): SessionManager {
        return SessionManager(context, keystoreManager, cryptoManager)
    }

    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        cryptoManager: CryptoManager
    ): PasswordEncryptionService {
        return PasswordEncryptionService(cryptoManager)
    }

    // ========== TIER 3: Audit & Persistence ==========

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

    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager {
        return AuditJournalManager(context, sessionManager, secureMemoryUtils)
    }

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

    @Provides
    @Singleton
    fun provideMasterAuditLogger(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        auditChainManager: AuditChainManager,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterAuditLogger {
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
     * ✅ FIXED: Changed PassbookDatabase to AppDatabase
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager
    ): AppDatabase {
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
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
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
