package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.MasterAuditLogger
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.PasswordEncryptionService
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager = KeystorePassphraseManager(context)

    @Provides
    @Singleton
    fun provideCryptoManager(
        secureMemoryUtils: SecureMemoryUtils
    ): CryptoManager {
        val manager = CryptoManager(secureMemoryUtils)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.initializeKeystore()
        }
        return manager
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager,
        cryptoManager: CryptoManager
    ): SessionManager = SessionManager(context, keystoreManager, cryptoManager)

    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        cryptoManager: CryptoManager
    ): PasswordEncryptionService = PasswordEncryptionService(cryptoManager)

    @Provides
    @Singleton
    fun provideAuditJournalManager(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): AuditJournalManager = AuditJournalManager(context, sessionManager, secureMemoryUtils)

    @Provides
    @Singleton
    fun provideAuditChainManager(
        @ApplicationContext context: Context,
        database: AppDatabase
    ): AuditChainManager = AuditChainManager(
        context = context,
        auditDao = database.auditDao()
    )

    @Provides
    @Singleton
    fun provideMasterAuditLogger(
        @ApplicationContext context: Context,
        auditJournalManager: AuditJournalManager,
        auditChainManager: AuditChainManager,
        sessionManager: SessionManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterAuditLogger = MasterAuditLogger(
        context = context,
        auditJournalManager = auditJournalManager,
        auditChainManager = auditChainManager,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager
    ): AppDatabase {
        val passphrase = keystoreManager.getPassphrase()
            ?: throw IllegalStateException("Failed to initialize database passphrase.")

        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "passbook_encrypted.db"
        )
            .openHelperFactory(SupportFactory(passphrase))
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
        return db
    }
}
