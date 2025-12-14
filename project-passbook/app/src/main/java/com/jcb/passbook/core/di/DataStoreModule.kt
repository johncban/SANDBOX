package com.jcb.passbook.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jcb.passbook.data.datastore.UserPreferences
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    // FIXED: UserPreferences takes Context, NOT DataStore!
    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        userPreferences: UserPreferences
    ): UserRepository {
        return UserRepository(userDao, userPreferences)
    }
}
