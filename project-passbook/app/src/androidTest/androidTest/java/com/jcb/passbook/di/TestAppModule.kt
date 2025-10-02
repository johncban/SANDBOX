package com.jcb.passbook.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.room.AppDatabase
import com.jcb.passbook.room.ItemDao
import com.jcb.passbook.room.UserDao
import com.jcb.passbook.util.CryptoManager
import com.jcb.passbook.util.test.MockCryptoManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule {

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager = MockCryptoManager()

    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

    @Provides
    @Singleton
    fun provideTestDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /***

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        PassbookDatabaseProvider.get(context)

    ***/



    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
}
