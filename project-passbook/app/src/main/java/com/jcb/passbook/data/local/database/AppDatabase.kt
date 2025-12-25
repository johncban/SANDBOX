package com.jcb.passbook.data.local.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.dao.CategoryDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.PasswordCategoryDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.entities.*
import timber.log.Timber

@Database(
    entities = [
        User::class,
        Item::class,
        Category::class,
        Audit::class,
        AuditMetadata::class,
        PasswordCategory::class
    ],
    version = 2,  // ✅ Updated from 1 to 2
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun auditDao(): AuditDao
    abstract fun auditMetadataDao(): AuditMetadataDao
    abstract fun passwordCategoryDao(): PasswordCategoryDao

    companion object {
        // ✅ CRITICAL: Safe migration from user_id to userId
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Timber.i("🔄 Executing MIGRATION 1→2: Renaming user_id to userId")

                try {
                    // Disable foreign key constraints during migration
                    database.execSQL("PRAGMA foreign_keys=OFF")

                    // Create new table with correct schema
                    database.execSQL("""
                        CREATE TABLE items_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            userId INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            username TEXT,
                            encryptedPassword BLOB NOT NULL,
                            category TEXT,
                            notes TEXT,
                            lastModified INTEGER NOT NULL,
                            FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                        )
                    """.trimIndent())

                    // Copy data from old table to new table
                    // Use COALESCE to handle NULL user_id values
                    database.execSQL("""
                        INSERT INTO items_new (id, userId, title, username, encryptedPassword, category, notes, lastModified)
                        SELECT id, COALESCE(user_id, 0), title, username, encryptedPassword, category, notes, lastModified
                        FROM items
                    """.trimIndent())

                    // Drop old table
                    database.execSQL("DROP TABLE items")

                    // Rename new table to original name
                    database.execSQL("ALTER TABLE items_new RENAME TO items")

                    // Recreate indices
                    database.execSQL("CREATE INDEX idx_items_userId ON items(userId)")
                    database.execSQL("CREATE INDEX idx_items_lastModified ON items(lastModified)")

                    // Re-enable foreign key constraints
                    database.execSQL("PRAGMA foreign_keys=ON")

                    Timber.i("✅ Migration 1→2 completed successfully")
                } catch (e: Exception) {
                    Timber.e(e, "❌ Migration 1→2 failed")
                    throw e
                }
            }
        }
    }
}

// In your Room.databaseBuilder() call, add:
// .addMigrations(AppDatabase.MIGRATION_1_2)
