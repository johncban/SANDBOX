package com.jcb.passbook.data.local.database

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entity.Item

/**
 * Room Database for Passbook
 *
 * Schema Versions:
 * v1: Item table with id, title, username, password, category, createdAt
 * v2: Added userId column to Item table for multi-user support
 */
@Database(
    entities = [Item::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        private const val TAG = "AppDatabase"

        /**
         * Migration from schema v1 to v2
         * Fixes: P0 - Missing migration (prevents data loss on app update!)
         *
         * Changes:
         * - Adds userId column to Item table
         * - Sets default userId to 'default' for existing data
         * - Maintains all other data
         * - Handles NULL values safely with COALESCE
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.i(TAG, "🔄 Running migration v1->v2...")

                try {
                    // Begin transaction for data integrity
                    database.beginTransaction()

                    // Step 1: Create new table with userId column
                    database.execSQL("""
                        CREATE TABLE item_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            username TEXT NOT NULL,
                            password TEXT NOT NULL,
                            category TEXT NOT NULL,
                            userId TEXT NOT NULL DEFAULT 'default',
                            createdAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}
                        )
                    """.trimIndent())

                    // Step 2: Copy data from old table to new table
                    // COALESCE handles NULL userId values safely
                    database.execSQL("""
                        INSERT INTO item_new (id, title, username, password, category, userId, createdAt)
                        SELECT 
                            id,
                            title,
                            username,
                            password,
                            category,
                            COALESCE(NULL, 'default') AS userId,
                            COALESCE(createdAt, ${System.currentTimeMillis()}) AS createdAt
                        FROM item
                    """.trimIndent())

                    // Step 3: Drop old table
                    database.execSQL("DROP TABLE item")

                    // Step 4: Rename new table to original name
                    database.execSQL("ALTER TABLE item_new RENAME TO item")

                    // Step 5: Recreate indices
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_item_userId ON item(userId)")

                    // Commit transaction
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

    // Abstract method to get ItemDao
    abstract fun itemDao(): ItemDao

    // NOTE: DO NOT include passwordCategoryDao() method
    // Fixes: P0 - Was causing crashes on database initialization
    // (never use - just access itemDao directly)
}
