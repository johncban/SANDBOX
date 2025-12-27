// File: app/src/main/java/com/jcb/passbook/core/di/SecurityModule.kt
package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditJournalManager
import com.jcb.passbook.security.audit.MasterAuditLogger
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.PasswordEncryptionService
import com.jcb.passbook.security.crypto.SecurityMemoryUtils
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.data.local.database.AppDatabase
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SecurityModule - Provides all security-related dependencies
 *
 * REFACTORED: Fixed missing closing braces
 * ✅ Argon2Kt provider added for password hashing
 * ✅ No database provider (moved to DatabaseModule)
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecurityMemoryUtils = SecurityMemoryUtils()

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
        secureMemoryUtils: SecurityMemoryUtils
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
        secureMemoryUtils: SecurityMemoryUtils
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
        secureMemoryUtils: SecurityMemoryUtils
    ): MasterAuditLogger = MasterAuditLogger(
        context = context,
        auditJournalManager = auditJournalManager,
        auditChainManager = auditChainManager,
        sessionManager = sessionManager,
        secureMemoryUtils = secureMemoryUtils
    )

    // ✅ NO DATABASE PROVIDER HERE
    // Database is ONLY provided by DatabaseModule.kt to prevent duplicate binding
}
