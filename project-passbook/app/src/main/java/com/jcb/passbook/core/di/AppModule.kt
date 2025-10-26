package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Room
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.DatabaseProvider
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.lambdapioneer.argon2kt.Argon2Kt
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideArgon2Kt(): Argon2Kt = Argon2Kt()

    // Note: AppDatabase is now provided through DatabaseProvider
    // which handles session-gated access and SQLCipher encryption
    
    @Provides
    fun provideItemDao(databaseProvider: DatabaseProvider): ItemDao {
        return object : ItemDao {
            private fun getDao() = databaseProvider.getDatabase().itemDao()
            
            override suspend fun insert(item: com.jcb.passbook.data.local.database.entities.Item): Long {
                return getDao().insert(item)
            }
            
            override suspend fun update(item: com.jcb.passbook.data.local.database.entities.Item) {
                getDao().update(item)
            }
            
            override suspend fun delete(item: com.jcb.passbook.data.local.database.entities.Item) {
                getDao().delete(item)
            }
            
            override suspend fun getAllItems(): List<com.jcb.passbook.data.local.database.entities.Item> {
                return getDao().getAllItems()
            }
            
            override suspend fun getItemsByUserId(userId: Int): List<com.jcb.passbook.data.local.database.entities.Item> {
                return getDao().getItemsByUserId(userId)
            }
            
            override suspend fun getItemById(id: Long): com.jcb.passbook.data.local.database.entities.Item? {
                return getDao().getItemById(id)
            }
        }
    }

    @Provides
    fun provideUserDao(databaseProvider: DatabaseProvider): UserDao {
        return object : UserDao {
            private fun getDao() = databaseProvider.getDatabase().userDao()
            
            override suspend fun insert(user: com.jcb.passbook.data.local.database.entities.User): Long {
                return getDao().insert(user)
            }
            
            override suspend fun getUserByUsername(username: String): com.jcb.passbook.data.local.database.entities.User? {
                return getDao().getUserByUsername(username)
            }
            
            override suspend fun getUserById(id: Int): com.jcb.passbook.data.local.database.entities.User? {
                return getDao().getUserById(id)
            }
            
            override suspend fun updateUser(user: com.jcb.passbook.data.local.database.entities.User) {
                getDao().updateUser(user)
            }
            
            override suspend fun deleteUser(user: com.jcb.passbook.data.local.database.entities.User) {
                getDao().deleteUser(user)
            }
        }
    }

    @Provides
    fun provideAuditDao(databaseProvider: DatabaseProvider): AuditDao {
        return object : AuditDao {
            private fun getDao() = databaseProvider.getDatabaseOrNull()?.auditDao()
                ?: throw IllegalStateException("Audit access requires active session")
            
            override suspend fun insert(auditEntry: com.jcb.passbook.data.local.database.entities.AuditEntry): Long {
                return getDao().insert(auditEntry)
            }
            
            override suspend fun insertAuditEntry(auditEntry: com.jcb.passbook.data.local.database.entities.AuditEntry): Long {
                return getDao().insertAuditEntry(auditEntry)
            }
            
            override suspend fun getAllAuditEntries(): List<com.jcb.passbook.data.local.database.entities.AuditEntry> {
                return getDao().getAllAuditEntries()
            }
            
            override suspend fun getAuditEntriesByUserId(userId: Int): List<com.jcb.passbook.data.local.database.entities.AuditEntry> {
                return getDao().getAuditEntriesByUserId(userId)
            }
            
            override suspend fun getAuditEntriesByEventType(eventType: String): List<com.jcb.passbook.data.local.database.entities.AuditEntry> {
                return getDao().getAuditEntriesByEventType(eventType)
            }
            
            override suspend fun getRecentAuditEntries(limit: Int): List<com.jcb.passbook.data.local.database.entities.AuditEntry> {
                return getDao().getRecentAuditEntries(limit)
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
    @RequiresApi(Build.VERSION_CODES.M)
    fun provideCryptoManager(): CryptoManager = CryptoManager()

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository =
        AuditRepository(auditDao)

    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager = SecurityAuditManager(auditLogger, auditDao, context)
}
