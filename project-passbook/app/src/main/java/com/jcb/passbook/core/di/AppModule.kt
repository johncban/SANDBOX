package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.*
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.PasswordEncryptionService
import com.jcb.passbook.security.session.SessionManager
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
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager
    ): AppDatabase {
        Timber.i("📦 Initializing encrypted database...")

        return try {
            val passphrase = keystoreManager.retrievePassphrase(context)
                ?: throw SecurityException("Failed to retrieve passphrase")

            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "passbook_vault.db"
            )
                .openHelperFactory(SupportFactory(passphrase))
                // ✅ CRITICAL: Include migration
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .enableMultiInstanceInvalidation()
                .build()
                .also {
                    Timber.i("✅ Database initialized")
                }
        } catch (e: Exception) {
            Timber.e(e, "❌ Database initialization failed")
            throw RuntimeException("Database init failed: ${e.message}", e)
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
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * ✅ NEW: Provide CryptoManager for encryption metadata
     */
    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        Timber.d("🔐 Initializing CryptoManager")
        return CryptoManager()
    }

    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager {
        Timber.d("🔑 Initializing KeystorePassphraseManager")
        return KeystorePassphraseManager(context)
    }

    /**
     * ✅ UPDATED: Now depends on CryptoManager
     */
    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        cryptoManager: CryptoManager
    ): PasswordEncryptionService {
        Timber.d("🔐 Initializing PasswordEncryptionService")
        return PasswordEncryptionService(cryptoManager)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        keystoreManager: KeystorePassphraseManager
    ): SessionManager {
        Timber.d("🔐 Initializing SessionManager")
        return SessionManager(context, keystoreManager)
    }
}
