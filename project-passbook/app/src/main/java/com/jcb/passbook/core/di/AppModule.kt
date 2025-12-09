package com.jcb.passbook.core.di

import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

private const val TAG = "AppModule"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ══════════════════════════════════════════════════════════════
    // ✅ CRITICAL FIX: Argon2Kt Provider
    // Resolves: [Dagger/MissingBinding]
    // com.lambdapioneer.argon2kt.Argon2Kt cannot be provided
    // ══════════════════════════════════════════════════════════════
    /**
     * Provides singleton instance of Argon2Kt for password hashing
     * Required by: UserViewModel for secure Argon2id password hashing
     *
     * ✅ This function MUST exist - it's required by UserViewModel
     */
    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt {
        Timber.tag(TAG).d("Providing Argon2Kt instance")
        return Argon2Kt()
    }

    // ══════════════════════════════════════════════════════════════
    // Repository Providers
    // ══════════════════════════════════════════════════════════════
    /**
     * Provides singleton ItemRepository for item data operations
     * Handles all CRUD operations for vault items (passwords, notes, etc.)
     */
    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository {
        Timber.tag(TAG).d("Providing ItemRepository")
        return ItemRepository(itemDao)
    }

    /**
     * Provides singleton UserRepository for user data operations
     * Handles all CRUD operations for user accounts and credentials
     */
    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository {
        Timber.tag(TAG).d("Providing UserRepository")
        return UserRepository(userDao)
    }
}
