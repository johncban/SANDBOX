package com.jcb.passbook.data.local.database

// Database Migration Script
// Add this to your AppDatabase class to handle the schema update
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Use a fixed timestamp instead of System.currentTimeMillis() for SQL constants
        val fixedTimestamp = 1698249600000L // Fixed timestamp for migration

        // Add new biometric columns to existing users table
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_token_salt BLOB")
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_token_hash BLOB")
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_enabled INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE User ADD COLUMN biometric_setup_at INTEGER")
        database.execSQL("ALTER TABLE User ADD COLUMN created_at INTEGER NOT NULL DEFAULT $fixedTimestamp")

        // Rename table to match new entity name
        database.execSQL("ALTER TABLE User RENAME TO users_temp")
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

@Database(
    entities = [User::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "passbook_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}