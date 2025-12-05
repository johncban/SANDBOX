package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.dao.CategoryDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Re-adding the CoroutineScope provider here since we removed the duplicate from SecurityModule
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "passbook_db"
        )
            // CRITICAL FIX: This wipes the database if the schema changes (e.g. Dev changes)
            // This solves the "Migration didn't properly handle: users" crash.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }

    @Provides
    @Singleton
    fun provideItemDao(appDatabase: AppDatabase): ItemDao {
        return appDatabase.itemDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(appDatabase: AppDatabase): CategoryDao {
        return appDatabase.categoryDao()
    }

    @Provides
    @Singleton
    fun provideAuditDao(appDatabase: AppDatabase): AuditDao {
        return appDatabase.auditDao()
    }

    @Provides
    @Singleton
    fun provideAuditMetadataDao(appDatabase: AppDatabase): AuditMetadataDao {
        return appDatabase.auditMetadataDao()
    }
}
