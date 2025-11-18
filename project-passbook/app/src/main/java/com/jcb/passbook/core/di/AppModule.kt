package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.SecureMemoryUtils
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule provides application-level dependencies.
 *
 * FIXED: Removed ALL audit-related providers to SecurityModule
 * This breaks the circular dependency cycles
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

    @Provides
    @Singleton
    fun provideSecureMemoryUtils(): SecureMemoryUtils = SecureMemoryUtils()

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
}
