package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.security.session.SessionManager
import com.jcb.passbook.security.session.SessionKeyProvider
import com.jcb.passbook.security.crypto.SessionPassphraseManager
import com.jcb.passbook.data.local.database.DatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for security-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context
    ): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideSessionKeyProvider(
        sessionManager: SessionManager
    ): SessionKeyProvider {
        return SessionKeyProvider(sessionManager)
    }

    @Provides
    @Singleton
    fun provideSessionPassphraseManager(
        @ApplicationContext context: Context
    ): SessionPassphraseManager {
        return SessionPassphraseManager(context)
    }

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
        sessionKeyProvider: SessionKeyProvider
    ): DatabaseProvider {
        return DatabaseProvider(context, sessionKeyProvider)
    }
}
