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
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
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

    // REMOVED: SessionKeyProvider and DatabaseProvider are now provided by SecurityModule

    @Provides
    fun provideItemDao(databaseProvider: DatabaseProvider): ItemDao {
        return object : ItemDao {
            private fun getDao() = databaseProvider.getDatabase().itemDao()

            override suspend fun insert(item: Item): Long {
                return getDao().insert(item)
            }

            override suspend fun update(item: Item) {
                getDao().update(item)
            }

            override suspend fun delete(item: Item) {
                getDao().delete(item)
            }

            override fun getItemsForUser(userId: Int): Flow<List<Item>> {
                return getDao().getItemsForUser(userId)
            }

            override fun getItem(id: Int, userId: Int): Flow<Item?> {
                return getDao().getItem(id, userId)
            }
        }
    }

    @Provides
    fun provideUserDao(databaseProvider: DatabaseProvider): UserDao {
        return object : UserDao {
            private fun getDao() = databaseProvider.getDatabase().userDao()

            override suspend fun insert(user: User): Long {
                return getDao().insert(user)
            }

            override suspend fun update(user: User) {
                getDao().update(user)
            }

            override suspend fun delete(user: User) {
                getDao().delete(user)
            }

            override fun getUser(id: Int): Flow<User> {
                return getDao().getUser(id)
            }

            override suspend fun getUserByUsername(username: String): User? {
                return getDao().getUserByUsername(username)
            }
        }
    }

    @Provides
    fun provideAuditDao(databaseProvider: DatabaseProvider): AuditDao {
        return object : AuditDao {
            private fun getDao() = databaseProvider.getDatabaseOrNull()?.auditDao()
                ?: throw IllegalStateException("Audit access requires active session")

            override suspend fun insert(auditEntry: AuditEntry): Long {
                return getDao().insert(auditEntry)
            }

            override suspend fun insertAll(auditEntries: List<AuditEntry>) {
                getDao().insertAll(auditEntries)
            }

            override fun getAuditEntriesForUser(userId: Int, limit: Int): Flow<List<AuditEntry>> {
                return getDao().getAuditEntriesForUser(userId, limit)
            }

            override fun getAuditEntriesByType(eventType: String, limit: Int): Flow<List<AuditEntry>> {
                return getDao().getAuditEntriesByType(eventType, limit)
            }

            override fun getAuditEntriesInTimeRange(startTime: Long, endTime: Long): Flow<List<AuditEntry>> {
                return getDao().getAuditEntriesInTimeRange(startTime, endTime)
            }

            override fun getFailedAuditEntries(limit: Int): Flow<List<AuditEntry>> {
                return getDao().getFailedAuditEntries(limit)
            }

            override fun getCriticalSecurityEvents(limit: Int): Flow<List<AuditEntry>> {
                return getDao().getCriticalSecurityEvents(limit)
            }

            override suspend fun countEventsSince(userId: Int?, eventType: String, since: Long): Int {
                return getDao().countEventsSince(userId, eventType, since)
            }

            override suspend fun deleteOldEntries(cutoffTime: Long): Int {
                return getDao().deleteOldEntries(cutoffTime)
            }

            override fun getAllAuditEntries(limit: Int): Flow<List<AuditEntry>> {
                return getDao().getAllAuditEntries(limit)
            }

            override suspend fun countEntriesWithoutChecksum(): Int {
                return getDao().countEntriesWithoutChecksum()
            }

            override suspend fun updateChecksum(id: Long, checksum: String) {
                getDao().updateChecksum(id, checksum)
            }
        }
    }

    @Provides
    @Singleton
    fun provideItemRepository(itemDao: ItemDao): ItemRepository = ItemRepository(itemDao)

    @Provides
    @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository = UserRepository(userDao)

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository =
        AuditRepository(auditDao)

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
    ): SecurityAuditManager = SecurityAuditManager(auditLogger, auditLogger, context)
}
