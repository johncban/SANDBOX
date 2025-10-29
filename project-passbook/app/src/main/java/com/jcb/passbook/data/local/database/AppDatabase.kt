package com.jcb.passbook.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.converters.DatabaseConverters
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.local.database.dao.UserDao
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Item::class, User::class, AuditEntry::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class) // CRITICAL: Add this line to handle List<String> and ByteArray
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun auditDao(): AuditDao // Audit logging support

    companion object {
        // Existing migration for version 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration (already in your codebase)
                database.execSQL("ALTER TABLE items ADD COLUMN new_column INTEGER DEFAULT 0")
            }
        }

        // Migration for version 3: adds the audit_entries table and indexes
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE audit_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER,
                        username TEXT,
                        timestamp INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        eventDescription TEXT NOT NULL,
                        sessionId TEXT,
                        metadata TEXT,
                        checksum TEXT,
                        FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create indexes for performance
                database.execSQL("CREATE INDEX index_audit_entries_userId ON audit_entries(userId)")
                database.execSQL("CREATE INDEX index_audit_entries_timestamp ON audit_entries(timestamp)")
                database.execSQL("CREATE INDEX index_audit_entries_eventType ON audit_entries(eventType)")
                database.execSQL("CREATE INDEX index_audit_entries_outcome ON audit_entries(outcome)")
            }
        }

        // SQLCipher factory - use this in your DI/AppModule for DB creation
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, "item_database")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration() // Only for development
                .build()
        }
    }
}