package com.jcb.passbook.di

import android.content.Context
import com.jcb.passbook.security.crypto.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityMemoryUtils(): SecurityMemoryUtils {
        return SecurityMemoryUtils()
    }

    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        securityMemoryUtils: SecurityMemoryUtils
    ): PasswordEncryptionService {
        return PasswordEncryptionService(securityMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        securityMemoryUtils: SecurityMemoryUtils
    ): SessionManager {
        return SessionManager(context, securityMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager,
        securityMemoryUtils: SecurityMemoryUtils
    ): MasterKeyManager {
        return MasterKeyManager(context, databaseKeyManager, securityMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        keystorePassphraseManager: KeystorePassphraseManager,
        securityMemoryUtils: SecurityMemoryUtils
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context, keystorePassphraseManager, securityMemoryUtils)
    }

    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager {
        return KeystorePassphraseManager(context)
    }

    @Provides
    @Singleton
    fun providePasswordHasher(): PasswordHasher {
        return PasswordHasher()
    }
}
