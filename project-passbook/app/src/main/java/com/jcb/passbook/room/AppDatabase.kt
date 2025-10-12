package com.jcb.passbook.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.jcb.passbook.data.local.dao.AuditDao
import com.jcb.passbook.data.local.dao.ItemDao
import com.jcb.passbook.data.local.dao.UserDao
import com.jcb.passbook.data.local.entities.Audit
import com.jcb.passbook.data.local.entities.Item
import com.jcb.passbook.data.local.entities.User

import net.sqlcipher.database.SupportFactory

/***    --------------------------------------  DO NOT DELETE  --------------------------------------
@Database(entities = [Item::class, User::class], version = 2, exportSchema = false)

abstract class AppDatabase : RoomDatabase() {

abstract fun itemDao(): ItemDao
abstract fun userDao(): UserDao

companion object {
// Migration object to handle database schema changes
val MIGRATION_1_2 = object : Migration(1, 2) {  // Adjust version numbers
override fun migrate(database: SupportSQLiteDatabase) {
// Define the migration logic here.  Example:
database.execSQL("ALTER TABLE Item ADD COLUMN new_column INTEGER DEFAULT 0")
}
}

fun create(context: Context, passphrase: ByteArray): AppDatabase {
val factory = SupportFactory(passphrase)
return androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "item_database")
.openHelperFactory(factory)
.addMigrations(MIGRATION_1_2) // Use proper migration
.build()
}
}
}
--------------------------------------  DO NOT DELETE  --------------------------------------      ***/

@Database(
    entities = [Item::class, User::class, Audit::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun auditDao(): AuditDao // Audit logging support

    companion object {
        // Existing migration for version 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration (already in your codebase)
                database.execSQL("ALTER TABLE item ADD COLUMN new_column INTEGER DEFAULT 0")
            }
        }

        // Migration for version 3: adds the audit_entry table and indexes
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
                        FOREIGN KEY(userId) REFERENCES user(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Create indexes for performance
                database.execSQL("CREATE INDEX index_audit_entry_userId ON audit_entry(userId)")
                database.execSQL("CREATE INDEX index_audit_entry_timestamp ON audit_entry(timestamp)")
                database.execSQL("CREATE INDEX index_audit_entry_eventType ON audit_entry(eventType)")
                database.execSQL("CREATE INDEX index_audit_entry_outcome ON audit_entry(outcome)")
            }
        }

        // SQLCipher factory - use this in your DI/AppModule for DB creation
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase)
            return androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "item_database")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}