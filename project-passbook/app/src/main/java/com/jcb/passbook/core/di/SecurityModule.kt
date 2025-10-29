package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.data.local.database.DatabaseProvider
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.crypto.SessionPassphraseManager
import com.jcb.passbook.security.session.SessionKeyProvider
import com.jcb.passbook.security.session.SessionManager
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
    fun provideSessionManager(
        @ApplicationContext context: Context
    ): SessionManager = SessionManager(context)

    @Provides
    @Singleton
    fun provideSessionKeyProvider(
        cryptoManager: CryptoManager // Only CryptoManager needed now
    ): SessionKeyProvider = SessionKeyProvider(cryptoManager)

    @Provides
    @Singleton
    fun provideSessionPassphraseManager(
        @ApplicationContext context: Context
    ): SessionPassphraseManager = SessionPassphraseManager(context)

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
        sessionKeyProvider: SessionKeyProvider
    ): DatabaseProvider = DatabaseProvider(context, sessionKeyProvider)
}
