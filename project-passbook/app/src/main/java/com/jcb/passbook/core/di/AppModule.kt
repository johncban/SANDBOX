package com.jcb.passbook.core.di

import android.content.Context
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.repository.ItemRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * ✅ VIBE CODED FIX: AppModule - COMPLETE & ERROR-FREE
 *
 * PROVIDES:
 * - All DAOs from AppDatabase (UserDao, ItemDao, AuditDao)
 * - @ApplicationScope CoroutineScope for async operations
 * - ItemRepository
 *
 * ERRORS FIXED:
 * ✅ ERROR 3: Added @ApplicationScope CoroutineScope provider
 * ✅ ERROR 4: Added AuditDao provider from AppDatabase
 * ✅ ERROR 5: Added ItemDao provider from AppDatabase
 * ✅ ERROR 6: Added UserDao provider from AppDatabase
 *
 * KEY ARCHITECTURAL DECISION:
 * - AppModule provides DATA LAYER (DAOs, Repositories, CoroutineScope)
 * - DataStoreModule provides USER PREFERENCES
 * - SecurityModule provides SECURITY COMPONENTS
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * ✅ FIXED ERROR 3: Provide Application-wide CoroutineScope
     *
     * CRITICAL: This must be provided in AppModule, not SecurityModule
     * Used by: AuditLogger, AuditQueue, and other async operations
     *
     * SupervisorJob ensures child coroutine failures don't cancel scope
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    /**
     * ✅ FIXED ERROR 6: Provide UserDao from AppDatabase
     *
     * REQUIRED BY:
     * - UserViewModel (via Hilt injection)
     * - UserRepository (via Hilt injection in DataStoreModule)
     *
     * Database provides: getAllUsers(), getUserById(), insert(), update(), delete()
     */
    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }

    /**
     * ✅ FIXED ERROR 5: Provide ItemDao from AppDatabase
     *
     * REQUIRED BY:
     * - ItemViewModel (via Hilt injection)
     * - ItemRepository (via Hilt injection in AppModule)
     *
     * Database provides: getItemsByUserId(), insertItem(), updateItem(), deleteItem()
     */
    @Provides
    @Singleton
    fun provideItemDao(appDatabase: AppDatabase): ItemDao {
        return appDatabase.itemDao()
    }

    /**
     * ✅ FIXED ERROR 4: Provide AuditDao from AppDatabase
     *
     * REQUIRED BY:
     * - SecurityAuditManager (via Hilt injection in SecurityModule)
     * - AuditQueue (via Hilt injection in SecurityModule)
     * - AuditChainManager (via Hilt injection in SecurityModule)
     * - AuditJournalManager (via Hilt injection in SecurityModule)
     *
     * Database provides: insertAuditLog(), getAuditLogs(), deleteOldLogs()
     */
    @Provides
    @Singleton
    fun provideAuditDao(appDatabase: AppDatabase): AuditDao {
        return appDatabase.auditDao()
    }

    /**
     * Provide ItemRepository
     *
     * DEPENDS ON: ItemDao (provided above)
     * USED BY: ItemViewModel
     */
    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository {
        return ItemRepository(itemDao)
    }
}

/**
 * ✅ Qualifier annotation for Application-scoped CoroutineScope
 *
 * PURPOSE:
 * Differentiates from other CoroutineScope instances in DI container
 * Allows multiple CoroutineScope providers without conflicts
 *
 * USAGE:
 * @Provides @Singleton @ApplicationScope fun provideScope(): CoroutineScope { ... }
 * @Inject fun constructor(@ApplicationScope scope: CoroutineScope) { ... }
 *
 * RETENTION: Binary means annotation only exists at compile-time
 * QUALIFIERS: Standard Dagger pattern for disambiguating dependencies
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope