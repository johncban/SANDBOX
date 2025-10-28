package com.jcb.passbook.data.local.database

import android.content.Context
import androidx.room.Room
import com.jcb.passbook.security.crypto.CryptoManager
import com.jcb.passbook.security.session.SessionKeyProvider
import net.sqlcipher.database.SupportFactory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Room database instances with SQLCipher encryption using session-derived passphrases
 * Enforces session state validation before database access
 */
@Singleton
class DatabaseProvider @Inject constructor(
    private val context: Context,
    private val sessionKeyProvider: SessionKeyProvider  // NOT CryptoManager
) {
    companion object {
        private const val DATABASE_NAME = "passbook_encrypted.db"
    }

    @Volatile
    private var databaseInstance: AppDatabase? = null

    @Volatile
    private var currentSessionId: String? = null

    /**
     * Gets the database instance, creating it with current session passphrase if needed
     * @throws SessionKeyProvider.SessionLockedException if session is not active
     */
    @Synchronized
    @Throws(SessionKeyProvider.SessionLockedException::class)
    fun getDatabase(): AppDatabase {
        val activeSessionId = sessionKeyProvider.getCurrentSessionId()
            ?: throw SessionKeyProvider.SessionLockedException("No active session for database access")

        // If session changed or no database instance, create new one
        if (databaseInstance == null || currentSessionId != activeSessionId) {
            Timber.d("Creating database instance for session: $activeSessionId")

            // Close existing database if session changed
            databaseInstance?.let { db ->
                if (db.isOpen) {
                    Timber.d("Closing previous database instance")
                    db.close()
                }
            }

            // Get session passphrase for SQLCipher
            val passphrase = sessionKeyProvider.requireSessionPassphrase()

            try {
                // Create SQLCipher support factory with session passphrase
                val supportFactory = SupportFactory(passphrase)

                // Build Room database with SQLCipher
                databaseInstance = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(supportFactory)
                    .fallbackToDestructiveMigration() // Remove for production
                    .build()

                currentSessionId = activeSessionId
                Timber.d("Database instance created successfully")

            } finally {
                // Always clear passphrase from memory
                passphrase.fill(0.toByte())
            }
        }

        return databaseInstance!!
    }

    /**
     * Gets database instance if available, null if session locked
     */
    fun getDatabaseOrNull(): AppDatabase? {
        return try {
            getDatabase()
        } catch (e: SessionKeyProvider.SessionLockedException) {
            Timber.d("Database access blocked - session locked")
            null
        }
    }

    /**
     * Closes the current database instance (called when session locks)
     */
    @Synchronized
    fun closeDatabase() {
        databaseInstance?.let { db ->
            if (db.isOpen) {
                Timber.d("Closing database instance")
                db.close()
            }
        }
        databaseInstance = null
        currentSessionId = null
    }

    /**
     * Validates that database can be accessed with current session
     */
    fun validateDatabaseAccess(): Boolean {
        return try {
            sessionKeyProvider.requireActiveSession()
            true
        } catch (e: SessionKeyProvider.SessionLockedException) {
            closeDatabase()
            false
        }
    }

    /**
     * Gets database with retry logic for transient failures
     */
    fun getDatabaseWithRetry(maxAttempts: Int = 3): AppDatabase? {
        repeat(maxAttempts) { attempt ->
            try {
                return getDatabase()
            } catch (e: Exception) {
                Timber.w(e, "Database access attempt ${attempt + 1} failed")
                if (attempt == maxAttempts - 1) {
                    // Last attempt failed
                    closeDatabase()
                    return null
                }
                // Brief delay before retry
                Thread.sleep(100)
            }
        }
        return null
    }
}
