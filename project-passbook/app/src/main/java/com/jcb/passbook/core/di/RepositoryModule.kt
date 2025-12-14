package com.jcb.passbook.core.di

import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.datastore.UserPreferences
import com.jcb.passbook.data.repositories.UserRepository  // Changed to repositories (plural)
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        userPreferences: UserPreferences
    ): UserRepository {
        return UserRepository(userDao, userPreferences)
    }
}
