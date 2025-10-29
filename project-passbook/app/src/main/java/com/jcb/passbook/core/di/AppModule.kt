package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.DatabaseProvider
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.jcb.passbook.security.crypto.CryptoManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

    @Provides
    @Singleton
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    // Room DAO adapters that read the current session database lazily each call
    @Provides
    fun provideItemDao(databaseProvider: DatabaseProvider): ItemDao =
        object : ItemDao {
            private fun real() = databaseProvider.getDatabase().itemDao()

            override suspend fun insert(item: Item): Long = real().insert(item)
            override suspend fun update(item: Item) = real().update(item)
            override suspend fun delete(item: Item) = real().delete(item)
            override fun getItemsForUser(userId: Int): Flow<List<Item>> = real().getItemsForUser(userId)
            override fun getItem(id: Int, userId: Int): Flow<Item?> = real().getItem(id, userId)
        }

    @Provides
    fun provideUserDao(databaseProvider: DatabaseProvider): UserDao =
        object : UserDao {
            private fun real() = databaseProvider.getDatabase().userDao()

            override suspend fun insert(user: User): Long = real().insert(user)
            override suspend fun update(user: User) = real().update(user)
            override suspend fun delete(user: User) = real().delete(user)
            override fun getUser(id: Int): Flow<User> = real().getUser(id)
            override suspend fun getUserByUsername(username: String): User? =
                real().getUserByUsername(username)
        }

    @Provides
    fun provideAuditDao(databaseProvider: DatabaseProvider): AuditDao =
        object : AuditDao {
            private fun real() = databaseProvider.getDatabase().auditDao()

            override suspend fun insert(auditEntry: AuditEntry): Long = real().insert(auditEntry)
            override suspend fun insertAll(auditEntries: List<AuditEntry>) = real().insertAll(auditEntries)
            override fun getAuditEntriesForUser(userId: Int, limit: Int) =
                real().getAuditEntriesForUser(userId, limit)
            override fun getAuditEntriesByType(eventType: String, limit: Int) =
                real().getAuditEntriesByType(eventType, limit)
            override fun getAuditEntriesInTimeRange(startTime: Long, endTime: Long) =
                real().getAuditEntriesInTimeRange(startTime, endTime)
            override fun getFailedAuditEntries(limit: Int) = real().getFailedAuditEntries(limit)
            override fun getCriticalSecurityEvents(limit: Int) = real().getCriticalSecurityEvents(limit)
            override suspend fun countEventsSince(userId: Int?, eventType: String, since: Long) =
                real().countEventsSince(userId, eventType, since)
            override suspend fun deleteOldEntries(cutoffTime: Long) = real().deleteOldEntries(cutoffTime)
            override fun getAllAuditEntries(limit: Int) = real().getAllAuditEntries(limit)
            override suspend fun countEntriesWithoutChecksum(): Int = real().countEntriesWithoutChecksum()
            override suspend fun updateChecksum(id: Long, checksum: String) = real().updateChecksum(id, checksum)
        }

    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository = ItemRepository(itemDao)

    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository = UserRepository(userDao)

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository = AuditRepository(auditDao)

    @Provides
    @Singleton
    fun provideAuditLogger(
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): AuditLogger = AuditLogger(auditDao, context)

    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager = SecurityAuditManager(auditLogger, auditDao, context)
}