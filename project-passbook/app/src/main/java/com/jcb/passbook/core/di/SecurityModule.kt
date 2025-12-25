package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.crypto.*
import com.jcb.passbook.security.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ✅ FIXED: SecurityModule with correct dependency order
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
    }

    /**
     * ✅ CRITICAL: KeystorePassphraseManager MUST be provided before DatabaseKeyManager
     * DatabaseModule depends on this to get the database passphrase
     */
    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager {
        return KeystorePassphraseManager(context)
    }

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        keystorePassphraseManager: KeystorePassphraseManager,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context, keystorePassphraseManager, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        secureMemoryUtils: SecureMemoryUtils
    ): PasswordEncryptionService {
        return PasswordEncryptionService(secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager {
        return SessionManager(context, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterKeyManager {
        return MasterKeyManager(context, databaseKeyManager, secureMemoryUtils)
    }

    @Provides
    @Singleton
    fun providePasswordHasher(): PasswordHasher {
        return PasswordHasher()
    }
}
