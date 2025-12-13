package com.jcb.passbook.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jcb.passbook.data.datastore.UserPreferences
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

private const val TAG = "AppModule"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ══════════════════════════════════════════════════════════════
    // ✅ FIX: UserPreferences Provider (CRITICAL)
    // Pass Context, NOT DataStore, because UserPreferences creates
    // its own DataStore internally
    // ══════════════════════════════════════════════════════════════
    /**
     * Provides singleton instance of UserPreferences for session management
     * REQUIRED by: UserRepository for current user ID persistence
     *
     * ✅ FIXED: Pass @ApplicationContext directly to UserPreferences
     */
    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        Timber.tag(TAG).d("Providing UserPreferences with Context")
        return UserPreferences(context)
    }

    // ══════════════════════════════════════════════════════════════
    // ✅ Argon2Kt Provider
    // ══════════════════════════════════════════════════════════════
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
     * ✅ NOW HAS REQUIRED UserPreferences PARAMETER
     */
    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        userPreferences: UserPreferences
    ): UserRepository {
        Timber.tag(TAG).d("Providing UserRepository")
        return UserRepository(userDao, userPreferences)
    }
}
