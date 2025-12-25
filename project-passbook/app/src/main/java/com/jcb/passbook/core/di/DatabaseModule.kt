package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.ItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import android.util.Log

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "passbook_database"
    private const val TAG = "DatabaseModule"

    /**
     * Provides the Room database instance
     * Fixes: P0 - Missing migration registration (prevents data loss!)
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        Log.i(TAG, "🔧 Initializing database...")

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        )
            // CRITICAL: Register migration 1->2
            // This prevents data loss when users update the app
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
            .also {
                Log.i(TAG, "✅ Database initialized successfully")
            }
    }

    /**
     * Provides ItemDao instance
     */
    @Provides
    @Singleton
    fun provideItemDao(database: AppDatabase): ItemDao {
        Log.d(TAG, "📦 Providing ItemDao")
        return database.itemDao()
    }
}
