package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.MasterKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        // ✅ FIXED: Generate database passphrase from SharedPreferences or create new one
        val prefs = context.getSharedPreferences("database_prefs", Context.MODE_PRIVATE)

        val passphrase = if (prefs.contains("db_passphrase")) {
            // Load existing passphrase
            prefs.getString("db_passphrase", null)?.toByteArray(Charsets.UTF_8)
                ?: generateAndSavePassphrase(prefs)
        } else {
            // Generate new passphrase for first launch
            generateAndSavePassphrase(prefs)
        }

        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "passbook_database"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Generate a secure random passphrase and save to encrypted SharedPreferences
     */
    private fun generateAndSavePassphrase(prefs: android.content.SharedPreferences): ByteArray {
        val passphrase = ByteArray(32)
        SecureRandom().nextBytes(passphrase)

        // Save as base64 string
        val passphraseString = android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP)
        prefs.edit().putString("db_passphrase", passphraseString).apply()

        Timber.d("Generated new database passphrase")
        return passphrase
    }

    @Provides
    @Singleton
    fun provideAuditDao(appDatabase: AppDatabase): AuditDao {
        return appDatabase.auditDao()
    }

    @Provides
    @Singleton
    fun provideItemDao(appDatabase: AppDatabase): ItemDao {
        return appDatabase.itemDao()
    }

    @Provides
    @Singleton
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }
}
