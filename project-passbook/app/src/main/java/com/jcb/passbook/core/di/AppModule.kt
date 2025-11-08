package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditChainManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule provides application-level dependencies.
 *
 * Database provisioning is handled by DatabaseModule.kt
 * This module focuses on repositories, crypto, and audit system dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ============================================================================================
    // CRYPTOGRAPHY PROVIDERS
    // ============================================================================================

    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    // ============================================================================================
    // REPOSITORY PROVIDERS
    // ============================================================================================

    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository =
        ItemRepository(itemDao)

    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository =
        UserRepository(userDao)

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository =
        AuditRepository(auditDao)

    // ============================================================================================
    // SECURITY AUDIT PROVIDERS
    // ============================================================================================

    /**
     * FIXED: Added AuditChainManager provider
     * AuditChainManager handles tamper-evident audit log chaining
     */
    @Provides
    @Singleton
    fun provideAuditChainManager(
        auditMetadataDao: AuditMetadataDao
    ): AuditChainManager = AuditChainManager(auditMetadataDao)

    /**
     * FIXED: Updated to include AuditChainManager parameter
     * Constructor signature: AuditLogger(AuditDao, AuditChainManager, Context)
     */
    @Provides
    @Singleton
    fun provideAuditLogger(
        auditDao: AuditDao,
        auditChainManager: AuditChainManager,
        @ApplicationContext context: Context
    ): AuditLogger = AuditLogger(auditDao, auditChainManager, context)

    /**
     * FIXED: Updated to include AuditDao parameter
     * Constructor signature: SecurityAuditManager(AuditLogger, AuditDao, Context)
     */
    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager = SecurityAuditManager(auditLogger, auditDao, context)
}