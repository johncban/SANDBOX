package com.jcb.passbook.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jcb.passbook.data.datastore.UserPreferences
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for DataStore and Repository Injection
 *
 * Provides:
 * - UserPreferences (DataStore-based preferences)
 * - UserRepository (Repository with DataStore integration)
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val USER_PREFERENCES = "user_preferences"

    /**
     * Provide DataStore instance for user preferences
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }

    /**
     * Provide UserPreferences singleton
     */
    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences {
        return UserPreferences(context)
    }

    /**
     * Provide UserRepository with UserDao and UserPreferences
     */
    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        userPreferences: UserPreferences
    ): UserRepository {
        return UserRepository(userDao, userPreferences)
    }
}

// Extension property for easy DataStore access
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)