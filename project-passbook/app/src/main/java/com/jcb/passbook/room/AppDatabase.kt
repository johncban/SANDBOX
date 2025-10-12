package com.jcb.passbook.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.jcb.passbook.data.local.dao.AuditDao
import com.jcb.passbook.data.local.dao.ItemDao
import com.jcb.passbook.data.local.dao.UserDao
import com.jcb.passbook.data.local.entities.AuditEntry
import com.jcb.passbook.data.local.entities.Item
import com.jcb.passbook.data.local.entities.User
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Item::class, User::class, AuditEntry::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun auditDao(): AuditDao

    companion object {
        // Migration from version 1 to 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to existing tables
                database.execSQL("ALTER TABLE item ADD COLUMN website TEXT")
                database.execSQL("ALTER TABLE item ADD COLUMN notes TEXT")
                database.execSQL("ALTER TABLE item ADD COLUMN categoryId TEXT")
                database.execSQL("ALTER TABLE item ADD COLUMN createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                database.execSQL("ALTER TABLE item ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                database.execSQL("ALTER TABLE item ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE item ADD COLUMN tags TEXT")
            }
        }

        // Migration from version 2 to 3: adds the audit_entry table and indexes
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create audit_entry table
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
                        FOREIGN KEY(userId) REFERENCES user(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Create indexes for performance
                database.execSQL("CREATE INDEX index_audit_entry_userId ON audit_entry(userId)")
                database.execSQL("CREATE INDEX index_audit_entry_timestamp ON audit_entry(timestamp)")
                database.execSQL("CREATE INDEX index_audit_entry_eventType ON audit_entry(eventType)")
                database.execSQL("CREATE INDEX index_audit_entry_outcome ON audit_entry(outcome)")

                // Update existing tables to use Long IDs if needed
                database.execSQL("""
                    CREATE TABLE user_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        username TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        biometricEnabled INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                        lastLoginAt INTEGER,
                        failedLoginAttempts INTEGER NOT NULL DEFAULT 0,
                        isLocked INTEGER NOT NULL DEFAULT 0,
                        lockUntil INTEGER
                    )
                """.trimIndent())

                database.execSQL("INSERT INTO user_new SELECT * FROM user")
                database.execSQL("DROP TABLE user")
                database.execSQL("ALTER TABLE user_new RENAME TO user")

                // Update item table
                database.execSQL("""
                    CREATE TABLE item_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        username TEXT,
                        encryptedPasswordData BLOB NOT NULL,
                        website TEXT,
                        notes TEXT,
                        categoryId TEXT,
                        userId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                        updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        tags TEXT,
                        FOREIGN KEY(userId) REFERENCES user(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                database.execSQL("INSERT INTO item_new SELECT * FROM item")
                database.execSQL("DROP TABLE item")
                database.execSQL("ALTER TABLE item_new RENAME TO item")
                database.execSQL("CREATE INDEX index_item_userId ON item(userId)")
            }
        }

        // Factory method for creating database with SQLCipher encryption
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase)
            return androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "passbook_database")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration() // Only for development
                .build()
        }
    }
}