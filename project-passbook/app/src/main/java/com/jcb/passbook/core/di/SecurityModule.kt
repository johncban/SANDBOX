package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Security Module - Provides all security-related dependencies
 * Order matters: dependencies must be provided before dependents
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
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
    fun provideCryptoManager(
        secureMemoryUtils: SecureMemoryUtils
    ): CryptoManager {
        return CryptoManager(secureMemoryUtils)
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
        cryptoManager: CryptoManager
    ): PasswordEncryptionService {
        return PasswordEncryptionService(cryptoManager)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        keystorePassphraseManager: KeystorePassphraseManager
    ): SessionManager {
        return SessionManager(context, keystorePassphraseManager)
    }

    @Provides
    @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): AuditLogger {
        return AuditLogger(context, cryptoManager)
    }

    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        keystorePassphraseManager: KeystorePassphraseManager,
        auditLogger: AuditLogger
    ): MasterKeyManager {
        return MasterKeyManager(context, keystorePassphraseManager) { auditLogger }
    }

    @Provides
    @Singleton
    fun providePasswordHasher(): PasswordHasher {
        return PasswordHasher()
    }
}
