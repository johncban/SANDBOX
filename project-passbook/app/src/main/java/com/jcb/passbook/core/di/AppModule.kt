package com.jcb.passbook.core.di

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Room
import com.jcb.passbook.data.repository.AuditRepository
import com.jcb.passbook.data.repository.ItemRepository
import com.jcb.passbook.data.repository.UserRepository
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.audit.SecurityAuditManager
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // The passphrase is securely managed by KeystorePassphraseManager,
        // which uses EncryptedSharedPreferences and MasterKey for security.
        val passphrase = KeystorePassphraseManager.getOrCreatePassphrase(context)
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
        val factory = SupportFactory(passphraseBytes)

        val builder = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "item_database"
        )
            .openHelperFactory(factory)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_3_2)


        // Enable fallback only for debug builds to ease development
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }
        return builder.build()
    }

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

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


    // -------------------------------------    SECURITY AUDIT  ----------------------------------------------  //
    @Provides
    fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()

    @Provides
    @Singleton
    fun provideAuditRepository(auditDao: AuditDao): AuditRepository =
        AuditRepository(auditDao)

    @Provides
    @Singleton
    fun provideAuditLogger(
        auditDao: AuditDao,                      // FIRST: AuditDao
        @ApplicationContext context: Context     // SECOND: Context
    ): AuditLogger = AuditLogger(auditDao, context)

    @Provides
    @Singleton
    fun provideSecurityAuditManager(
        auditLogger: AuditLogger,
        auditDao: AuditDao,
        @ApplicationContext context: Context
    ): SecurityAuditManager = SecurityAuditManager(auditLogger, auditDao, context)
}
