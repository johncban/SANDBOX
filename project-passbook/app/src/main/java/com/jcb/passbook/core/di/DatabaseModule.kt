package com.jcb.passbook.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.*
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        dbKeyManager: DatabaseKeyManager,
        auditLogger: Lazy<AuditLogger>  // ✅ Lazy injection
    ): AppDatabase {
        return try {
            SQLiteDatabase.loadLibs(context)

            val passphrase: ByteArray = runBlocking {
                dbKeyManager.getOrCreateDatabasePassphrase()
                    ?: throw IllegalStateException("Cannot create database without passphrase")
            }

            val factory = SupportFactory(passphrase)
            java.util.Arrays.fill(passphrase, 0.toByte())

            val database = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "passbook_encrypted.db"
            )
                .openHelperFactory(factory)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(DatabaseCallback { auditLogger.get() })  // ✅ Pass as lambda
                .build()

            auditLogger.get().logUserAction(
                userId = null,
                username = "SYSTEM",
                eventType = AuditEventType.SYSTEM_EVENT,
                action = "Database initialized with SQLCipher encryption",
                resourceType = "DATABASE",
                resourceId = "passbook_encrypted.db",
                outcome = AuditOutcome.SUCCESS,
                errorMessage = null,
                securityLevel = "HIGH"
            )

            database
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize encrypted database")

            try {
                auditLogger.get().logUserAction(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.SYSTEM_EVENT,
                    action = "Database initialization failed",
                    resourceType = "DATABASE",
                    resourceId = "passbook_encrypted.db",
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.message,
                    securityLevel = "CRITICAL"
                )
            } catch (ignored: Exception) {
                Timber.e(ignored, "Could not log database error")
            }

            throw SecurityException("Failed to initialize secure database", e)
        }
    }

    private class DatabaseCallback(
        private val auditLoggerProvider: () -> AuditLogger
    ) : RoomDatabase.Callback() {
        // Same implementation as before, using auditLoggerProvider()
    }

    @Provides
    fun provideItemDao(database: AppDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideAuditDao(database: AppDatabase): AuditDao = database.auditDao()

    @Provides
    fun provideAuditMetadataDao(database: AppDatabase): AuditMetadataDao =
        database.auditMetadataDao()
}
