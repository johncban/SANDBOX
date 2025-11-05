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

// FIXED: Updated database version to 4 and added proper migrations
@Database(
    entities = [Item::class, User::class, AuditEntry::class, AuditMetadata::class],
    version = 4,
    exportSchema = false
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
                database.execSQL("ALTER TABLE Item ADD COLUMN new_column INTEGER DEFAULT 0")
            }
        }

        // Migration from version 2 to 3: adds the audit_entry table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE audit_entry (
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
                        outcome TEXT NOT NULL,
                        errorMessage TEXT,
                        securityLevel TEXT NOT NULL DEFAULT 'NORMAL',
                        ipAddress TEXT,
                        checksum TEXT,
                        FOREIGN KEY(userId) REFERENCES User(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Create indexes for performance
                database.execSQL("CREATE INDEX index_audit_entry_userId ON audit_entry(userId)")
                database.execSQL("CREATE INDEX index_audit_entry_timestamp ON audit_entry(timestamp)")
                database.execSQL("CREATE INDEX index_audit_entry_eventType ON audit_entry(eventType)")
                database.execSQL("CREATE INDEX index_audit_entry_outcome ON audit_entry(outcome)")
            }
        }

        // FIXED: Migration from version 3 to 4: adds tamper-evident chaining and metadata table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add chain fields to audit_entry
                database.execSQL("ALTER TABLE audit_entry ADD COLUMN chainPrevHash TEXT")
                database.execSQL("ALTER TABLE audit_entry ADD COLUMN chainHash TEXT")

                // Create audit_metadata table
                database.execSQL("""
                    CREATE TABLE audit_metadata (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        description TEXT
                    )
                """.trimIndent())

                // Create additional indexes for chain verification performance
                database.execSQL("CREATE INDEX index_audit_entry_chainHash ON audit_entry(chainHash)")
                database.execSQL("CREATE INDEX index_audit_entry_sessionId ON audit_entry(sessionId)")
                database.execSQL("CREATE INDEX index_audit_entry_securityLevel ON audit_entry(securityLevel)")

                // Initialize chain metadata
                database.execSQL("""
                    INSERT INTO audit_metadata (key, value, timestamp, description) 
                    VALUES ('audit_chain_head', '0000000000000000000000000000000000000000000000000000000000000000', 
                            ${System.currentTimeMillis()}, 'Genesis chain head hash')
                """.trimIndent())
            }
        }

        // FIXED: Enhanced database creation with security features
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase, null, false)

            return Room.databaseBuilder(context, AppDatabase::class.java, "item_database")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) // Added new migration
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Enable WAL mode for better performance and concurrent access
                        db.execSQL("PRAGMA journal_mode=WAL")
                        // Increase SQLCipher iterations for enhanced security
                        db.execSQL("PRAGMA cipher_iterations=256000")
                        // Enable foreign keys
                        db.execSQL("PRAGMA foreign_keys=ON")
                        // Set secure delete to overwrite deleted data
                        db.execSQL("PRAGMA secure_delete=ON")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Ensure security settings are applied on every open
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.execSQL("PRAGMA secure_delete=ON")
                    }
                })
                .build()
        }

        // FIXED: Alternative creation method using DatabaseKeyManager with proper cleanup
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
    }
}