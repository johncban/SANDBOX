package com.jcb.passbook.core.di  // ✅ FIXED: Correct package name

import android.content.Context
import com.jcb.passbook.security.crypto.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SecurityModule - Hilt dependency injection for security components
 *
 * ✅ FIXED: Correct package name (com.jcb.passbook.core.di)
 * ✅ FIXED: All providers correctly typed
 * ✅ FIXED: Proper dependency chain for key managers
 *
 * Provides:
 * - SecureMemoryUtils (secure memory operations)
 * - PasswordEncryptionService (password encryption)
 * - SessionManager (user session management)
 * - MasterKeyManager (master key operations)
 * - DatabaseKeyManager (database key management)
 * - KeystorePassphraseManager (keystore operations)
 * - PasswordHasher (Argon2 password hashing)
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * Provides SecureMemoryUtils singleton
     * Base utility for secure memory operations
     */
    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils {
        return SecureMemoryUtils()
    }

    /**
     * Provides PasswordEncryptionService
     * Handles encryption/decryption of password vault items
     */
    @Provides
    @Singleton
    fun providePasswordEncryptionService(
        secureMemoryUtils: SecureMemoryUtils
    ): PasswordEncryptionService {
        return PasswordEncryptionService(secureMemoryUtils)
    }

    /**
     * Provides SessionManager
     * Manages user authentication sessions with biometric support
     */
    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        secureMemoryUtils: SecureMemoryUtils
    ): SessionManager {
        return SessionManager(context, secureMemoryUtils)
    }

    /**
     * Provides MasterKeyManager
     * Manages the master encryption key for the application
     */
    @Provides
    @Singleton
    fun provideMasterKeyManager(
        @ApplicationContext context: Context,
        databaseKeyManager: DatabaseKeyManager,
        secureMemoryUtils: SecureMemoryUtils
    ): MasterKeyManager {
        return MasterKeyManager(context, databaseKeyManager, secureMemoryUtils)
    }

    /**
     * Provides DatabaseKeyManager
     * Manages encryption keys for SQLCipher database
     */
    @Provides
    @Singleton
    fun provideDatabaseKeyManager(
        @ApplicationContext context: Context,
        keystorePassphraseManager: KeystorePassphraseManager,
        secureMemoryUtils: SecureMemoryUtils
    ): DatabaseKeyManager {
        return DatabaseKeyManager(context, keystorePassphraseManager, secureMemoryUtils)
    }

    /**
     * Provides KeystorePassphraseManager
     * Manages passphrases stored in Android Keystore
     */
    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager {
        return KeystorePassphraseManager(context)
    }

    /**
     * Provides PasswordHasher
     * Handles Argon2 password hashing for user authentication
     */
    @Provides
    @Singleton
    fun providePasswordHasher(): PasswordHasher {
        return PasswordHasher()
    }
}
