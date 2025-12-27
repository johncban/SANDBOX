package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jcb.passbook.security.crypto.*
import com.lambdapioneer.argon2kt.Argon2Kt
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
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

    @Provides
    @Singleton
    fun provideKeystorePassphraseManager(
        @ApplicationContext context: Context
    ): KeystorePassphraseManager = KeystorePassphraseManager(context)

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

    // ✅ NO AUDIT PROVIDERS - MOVED TO AuditModule
}
