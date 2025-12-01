package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.utils.memory.MemoryManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilsModule {

    @Provides
    @Singleton
    fun provideMemoryManager(
        @ApplicationContext context: Context
    ): MemoryManager {
        return MemoryManager(context)
    }
}
