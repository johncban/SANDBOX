package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.PasswordEncryptionService
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SecurityModule - Provides ONLY crypto/security dependencies
 *
 * REFACTORED:
 * ✅ Removed all audit providers (moved to AuditModule)
 * ✅ Fixed missing closing braces
 * ✅ Clear separation of concerns
 *
 * DO NOT add audit-related providers here!
 */
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

    /**
     * Provides Argon2Kt for password hashing
     * CRITICAL: Required by UserViewModel for secure password hashing
     */
    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

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

    // ✅ NO AUDIT PROVIDERS HERE - MOVED TO AuditModule
    // ✅ NO DATABASE PROVIDER HERE - USE DatabaseModule
}
