package com.jcb.passbook.data.local.database

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.local.database.entities.Category

/**
 * Room Database for Passbook
 *
 * CRITICAL FIX: Added all entities with foreign key relationships
 * - Item (has foreign keys to User and Category)
 * - User (referenced by Item)
 * - Category (referenced by Item)
 *
 * Schema Versions:
 * v1: Initial schema
 * v2: Added userId column and multi-user support
 */
@Database(
    entities = [
        Item::class,      // ✅ FIXED: Correct import path
        User::class,      // ✅ ADDED: Required for foreign key
        Category::class   // ✅ ADDED: Required for foreign key
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        private const val TAG = "AppDatabase"

        /**
         * Migration from schema v1 to v2
         * CRITICAL: This migration handles multi-table schema changes
         *
         * Changes:
         * - Creates users table
         * - Creates categories table
         * - Migrates items table to include userId and foreign keys
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "🔄 Running migration v1->v2...")

                try {
                    database.beginTransaction()

                    // Step 1: Create users table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            username TEXT NOT NULL,
                            password_hash BLOB NOT NULL,
                            salt BLOB NOT NULL,
                            created_at INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                            last_login_at INTEGER,
                            is_active INTEGER NOT NULL DEFAULT 1
                        )
                    """.trimIndent())

                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users(username)")

                    // Step 2: Create categories table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS categories (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT,
                            icon TEXT,
                            color TEXT,
                            parent_id INTEGER,
                            user_id INTEGER NOT NULL,
                            sort_order INTEGER NOT NULL DEFAULT 0,
                            is_visible INTEGER NOT NULL DEFAULT 1,
                            is_system INTEGER NOT NULL DEFAULT 0,
                            item_count INTEGER NOT NULL DEFAULT 0,
                            created_at INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                            updated_at INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}
                        )
                    """.trimIndent())

                    database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_user_id ON categories(user_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parent_id ON categories(parent_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_categories_sort_order ON categories(sort_order)")

                    // Step 3: Create default user for existing data
                    database.execSQL("""
                        INSERT OR IGNORE INTO users (id, username, password_hash, salt, created_at, is_active)
                        VALUES (1, 'default', X'00', X'00', ${System.currentTimeMillis()}, 1)
                    """.trimIndent())

                    // Step 4: Migrate items table
                    database.execSQL("""
                        CREATE TABLE items_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            userId INTEGER NOT NULL DEFAULT 1,
                            title TEXT NOT NULL,
                            username TEXT,
                            encryptedPassword BLOB NOT NULL,
                            url TEXT,
                            notes TEXT,
                            category_id INTEGER,
                            category_name TEXT,
                            password_category TEXT NOT NULL DEFAULT 'OTHER',
                            isFavorite INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                            updatedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()},
                            lastAccessedAt INTEGER,
                            FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,
                            FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL
                        )
                    """.trimIndent())

                    // Step 5: Copy existing data if items table exists
                    val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='items'")
                    val itemsTableExists = cursor.moveToFirst()
                    cursor.close()

                    if (itemsTableExists) {
                        database.execSQL("""
                            INSERT INTO items_new (
                                id, userId, title, username, encryptedPassword, 
                                url, notes, category_id, category_name, password_category,
                                isFavorite, createdAt, updatedAt, lastAccessedAt
                            )
                            SELECT 
                                id,
                                1 AS userId,
                                title,
                                username,
                                CAST(password AS BLOB) AS encryptedPassword,
                                url,
                                notes,
                                NULL AS category_id,
                                category AS category_name,
                                'OTHER' AS password_category,
                                0 AS isFavorite,
                                COALESCE(createdAt, ${System.currentTimeMillis()}) AS createdAt,
                                ${System.currentTimeMillis()} AS updatedAt,
                                NULL AS lastAccessedAt
                            FROM items
                        """.trimIndent())

                        database.execSQL("DROP TABLE items")
                    }

                    // Step 6: Rename new table
                    database.execSQL("ALTER TABLE items_new RENAME TO items")

                    // Step 7: Create indices for items table
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_items_userId ON items(userId)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_items_category_id ON items(category_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_items_password_category ON items(password_category)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_items_isFavorite ON items(isFavorite)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_items_createdAt ON items(createdAt)")

                    database.setTransactionSuccessful()
                    Log.i(TAG, "✅ Migration v1->v2 completed successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Migration v1->v2 failed: ${e.message}", e)
                    throw e
                } finally {
                    database.endTransaction()
                }
            }
        }
    }

    // Abstract DAO methods
    abstract fun itemDao(): ItemDao

    // TODO: Add these when you create the DAOs
    // abstract fun userDao(): UserDao
    // abstract fun categoryDao(): CategoryDao
}
