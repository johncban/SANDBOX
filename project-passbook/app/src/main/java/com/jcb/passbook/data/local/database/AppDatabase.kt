package com.jcb.passbook.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.User

// Database Migration from version 1 to 2
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val fixedTimestamp = 1698249600000L // Fixed timestamp for migration

        // Add new biometric columns to existing users table
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_token_salt BLOB")
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_token_hash BLOB")
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_enabled INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_setup_at INTEGER")
        database.execSQL("ALTER TABLE User ADD COLUMN created_at INTEGER NOT NULL DEFAULT $fixedTimestamp")

        // Create audit_entry table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_entry (
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
        """)

        // Rename User table to match new schema if needed
        database.execSQL("CREATE TABLE IF NOT EXISTS users_temp AS SELECT * FROM User")
        database.execSQL("DROP TABLE User")
        database.execSQL("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                username TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL DEFAULT $fixedTimestamp,
                biometric_token_salt BLOB,
                biometric_token_hash BLOB,
                biometric_enabled INTEGER NOT NULL DEFAULT 0,
                biometric_setup_at INTEGER
            )
        """)
        database.execSQL("""
            INSERT INTO users (id, username, password_hash, created_at, biometric_enabled)
            SELECT id, username, passwordHash, $fixedTimestamp, 0
            FROM users_temp
        """)
        database.execSQL("DROP TABLE users_temp")
    }
}

val MIGRATION_3_2 = object : Migration(3, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Handle downgrade from version 3 to 2
        // Drop any columns/tables added in version 3
        // This is a destructive operation - data will be lost
        database.execSQL("DROP TABLE IF EXISTS some_v3_table") // Example

        // Recreate tables to match version 2 schema
        // Add specific SQL commands based on what changed between v2 and v3
    }
}

@Database(
    entities = [
        User::class,
        Item::class,
        AuditEntry::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // Abstract DAO methods
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun auditDao(): AuditDao

    companion object {
        // Migration constant for DI module
        val MIGRATION_1_2 = com.jcb.passbook.data.local.database.MIGRATION_1_2
        val MIGRATION_3_2 = com.jcb.passbook.data.local.database.MIGRATION_3_2
    }
}