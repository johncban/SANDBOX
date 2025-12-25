package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for database dependency injection
 *
 * ✅ FIXED: Removed providePasswordCategoryDao() since AppDatabase doesn't have passwordCategoryDao()
 *
 * Available DAOs:
 * - ItemDao
 * - UserDao
 * - CategoryDao
 * - AuditDao
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "passbook_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    // ✅ REMOVED: providePasswordCategoryDao() - Not in AppDatabase
    // If you need this in the future, add it to AppDatabase first:
    // abstract fun passwordCategoryDao(): PasswordCategoryDao

    @Provides
    @Singleton
    fun provideAuditDao(database: AppDatabase): AuditDao = database.auditDao()
}
