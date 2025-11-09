package com.jcb.passbook.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditMetadata
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory

/**
 * AppDatabase - Main Room database for PassBook app
 *
 * FIXES APPLIED:
 * - Updated database version to 4 with proper migrations
 * - Fixed table name in migrations: 'audit_entry' -> 'audit_entries'
 * - Fixed table name in Item queries to match entity tableName 'items'
 * - Proper index naming to match table names
 * - Enhanced security settings with SQLCipher
 */
@Database(
    entities = [Item::class, User::class, AuditEntry::class, AuditMetadata::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun auditDao(): AuditDao
    abstract fun auditMetadataDao(): AuditMetadataDao

    companion object {
        // Migration from version 1 to 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add any new columns or tables for version 2
                database.execSQL("ALTER TABLE items ADD COLUMN lastAccessedAt INTEGER")
            }
        }

        // Migration from version 2 to 3: adds the audit_entries table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // FIXED: Changed table name from 'audit_entry' to 'audit_entries'
                database.execSQL("""
                    CREATE TABLE audit_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER,
                        username TEXT,
                        timestamp INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        action TEXT NOT NULL,
                        resourceType TEXT,
                        resourceId TEXT,
                        deviceInfo TEXT,
                        appVersion TEXT,
                        sessionId TEXT,
                        outcome TEXT NOT NULL DEFAULT 'SUCCESS',
                        errorMessage TEXT,
                        securityLevel TEXT NOT NULL DEFAULT 'NORMAL',
                        ipAddress TEXT,
                        userAgent TEXT,
                        location TEXT,
                        checksum TEXT,
                        FOREIGN KEY(userId) REFERENCES users(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // FIXED: Create indexes with correct table name 'audit_entries'
                database.execSQL("CREATE INDEX index_audit_entries_userId ON audit_entries(userId)")
                database.execSQL("CREATE INDEX index_audit_entries_timestamp ON audit_entries(timestamp)")
                database.execSQL("CREATE INDEX index_audit_entries_eventType ON audit_entries(eventType)")
                database.execSQL("CREATE INDEX index_audit_entries_outcome ON audit_entries(outcome)")
            }
        }

        // Migration from version 3 to 4: adds tamper-evident chaining and metadata table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // FIXED: Add chain fields to audit_entries (not audit_entry)
                database.execSQL("ALTER TABLE audit_entries ADD COLUMN chainPrevHash TEXT")
                database.execSQL("ALTER TABLE audit_entries ADD COLUMN chainHash TEXT")

                // Create audit_metadata table
                database.execSQL("""
                    CREATE TABLE audit_metadata (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        description TEXT
                    )
                """.trimIndent())

                // FIXED: Create additional indexes for chain verification on correct table
                database.execSQL("CREATE INDEX index_audit_entries_chainHash ON audit_entries(chainHash)")
                database.execSQL("CREATE INDEX index_audit_entries_sessionId ON audit_entries(sessionId)")
                database.execSQL("CREATE INDEX index_audit_entries_securityLevel ON audit_entries(securityLevel)")

                // Initialize chain metadata
                database.execSQL("""
                    INSERT INTO audit_metadata (key, value, timestamp, description) 
                    VALUES ('audit_chain_head', '0000000000000000000000000000000000000000000000000000000000000000', 
                            ${'$'}{System.currentTimeMillis()}, 'Genesis chain head hash')
                """.trimIndent())
            }
        }

        /**
         * Create database with encryption
         *
         * @param context Application context
         * @param passphrase Encryption passphrase (securely managed)
         * @return Configured AppDatabase instance
         */
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase, null, false)

            return Room.databaseBuilder(context, AppDatabase::class.java, "passbook_database")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Enable WAL mode for better performance and concurrent access
                        db.execSQL("PRAGMA journal_mode=WAL")
                        // Increase SQLCipher iterations for enhanced security (FIPS 140-2 compliance)
                        db.execSQL("PRAGMA cipher_iterations=256000")
                        // Enable foreign keys
                        db.execSQL("PRAGMA foreign_keys=ON")
                        // Set secure delete to overwrite deleted data
                        db.execSQL("PRAGMA secure_delete=ON")
                        // Additional security settings
                        db.execSQL("PRAGMA cipher_memory_security=ON")
                        db.execSQL("PRAGMA cipher_plaintext_header_size=0")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Ensure security settings are applied on every open
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.execSQL("PRAGMA secure_delete=ON")
                        db.execSQL("PRAGMA cipher_memory_security=ON")
                    }
                })
                .fallbackToDestructiveMigration() // Only for development - remove in production
                .build()
        }

        /**
         * Create database using DatabaseKeyManager
         *
         * @param context Application context
         * @param databaseKeyManager Key manager for secure passphrase retrieval
         * @return AppDatabase instance or null if key retrieval fails
         */
        suspend fun createWithKeyManager(
            context: Context,
            databaseKeyManager: DatabaseKeyManager
        ): AppDatabase? {
            val passphrase = databaseKeyManager.getOrCreateDatabasePassphrase()
            return if (passphrase != null) {
                try {
                    create(context, passphrase)
                } finally {
                    // Securely wipe passphrase from memory
                    java.security.SecureRandom().nextBytes(passphrase)
                    passphrase.fill(0)
                }
            } else {
                null
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get singleton database instance
         *
         * @param context Application context
         * @param passphrase Database encryption key
         * @return AppDatabase singleton instance
         */
        fun getDatabase(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = create(context.applicationContext, passphrase)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clear database instance (for testing or re-initialization)
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}