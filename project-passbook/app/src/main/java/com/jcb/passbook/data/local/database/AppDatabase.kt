package com.jcb.passbook.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcb.passbook.data.local.database.dao.AuditDao
import com.jcb.passbook.data.local.database.dao.AuditMetadataDao
import com.jcb.passbook.data.local.database.dao.CategoryDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.AuditEntry
import com.jcb.passbook.data.local.database.entities.AuditMetadata
import com.jcb.passbook.data.local.database.entities.Category
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import net.sqlcipher.database.SupportFactory
import timber.log.Timber

/**
 * AppDatabase - Main Room database for PassBook app
 *
 * ✅ COMPLETE REFACTORED VERSION - All Critical Fixes Applied
 *
 * Database Version: 7
 *
 * ✅ FIXES APPLIED:
 * 1. Removed .fallbackToDestructiveMigration() for production safety
 * 2. Added @Suppress("DEPRECATION") for SupportFactory compatibility
 * 3. Added Migration 6→7 for 'type' field in items table
 * 4. Proper TAG logging for all migrations
 *
 * Entities:
 * - User: User accounts and authentication
 * - Item: Password/credential entries (with type field)
 * - Category: Organizational categories for items
 * - AuditEntry: Security audit trail entries (tableName = "audit_log")
 * - AuditMetadata: Audit system metadata
 *
 * Security Features:
 * - SQLCipher encryption with 256,000 PBKDF2 iterations
 * - Foreign key constraints enforced
 * - Tamper-evident audit chaining
 * - Secure delete (overwrites deleted data)
 * - Memory security enabled
 * - WAL mode for concurrency
 *
 * Migration History:
 * - v1→v2: Added lastAccessedAt to items
 * - v2→v3: Added audit_entries table
 * - v3→v4: Added audit chaining and metadata
 * - v4→v5: Added categories table
 * - v5→v6: Added category foreign key to items
 * - v6→v7: Added type field to items (password/note/card)
 */
@Database(
    entities = [
        User::class,
        Item::class,
        Category::class,
        AuditEntry::class,
        AuditMetadata::class
    ],
    version = 7,  // ✅ Updated from 6 to 7
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // ============================================
    // DAO Declarations
    // ============================================

    abstract fun userDao(): UserDao
    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun auditDao(): AuditDao
    abstract fun auditMetadataDao(): AuditMetadataDao

    companion object {
        private const val TAG = "AppDatabase"

        // ============================================
        // DATABASE MIGRATIONS
        // ============================================

        /**
         * Migration 1 → 2: Add lastAccessedAt column to items table
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.tag(TAG).i("Running migration 1 → 2: Adding lastAccessedAt")
                db.execSQL("ALTER TABLE items ADD COLUMN lastAccessedAt INTEGER")
                Timber.tag(TAG).i("✓ Migration 1 → 2 completed")
            }
        }

        /**
         * Migration 2 → 3: Add audit_entries table for security tracking
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.tag(TAG).i("Running migration 2 → 3: Creating audit_log table")

                // Create audit_entries table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        event_type TEXT NOT NULL,
                        user_id INTEGER,
                        description TEXT NOT NULL,
                        value TEXT,
                        chain_prev_hash TEXT,
                        chain_hash TEXT,
                        checksum TEXT,
                        security_level TEXT NOT NULL DEFAULT 'NORMAL',
                        outcome TEXT NOT NULL DEFAULT 'SUCCESS',
                        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
                    )
                """.trimIndent())

                // Create indexes for audit queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_timestamp ON audit_log(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_event_type ON audit_log(event_type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_user_id ON audit_log(user_id)")

                Timber.tag(TAG).i("✓ Migration 2 → 3 completed")
            }
        }

        /**
         * Migration 3 → 4: Add tamper-evident chaining and metadata table
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.tag(TAG).i("Running migration 3 → 4: Adding audit metadata")

                // Create audit_metadata table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS audit_metadata (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        description TEXT
                    )
                """.trimIndent())

                // Create additional indexes for chain verification
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_chain_hash ON audit_log(chain_hash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_security_level ON audit_log(security_level)")

                // Initialize chain metadata
                val timestamp = System.currentTimeMillis()
                db.execSQL("""
                    INSERT OR IGNORE INTO audit_metadata (key, value, timestamp, description)
                    VALUES (
                        'audit_chain_head',
                        '0000000000000000000000000000000000000000000000000000000000000000',
                        $timestamp,
                        'Genesis chain head hash'
                    )
                """.trimIndent())

                Timber.tag(TAG).i("✓ Migration 3 → 4 completed")
            }
        }

        /**
         * Migration 4 → 5: Add categories table for item organization
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.tag(TAG).i("Running migration 4 → 5: Creating categories table")

                // Create categories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        icon TEXT,
                        color TEXT,
                        parent_id INTEGER,
                        user_id INTEGER,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        is_visible INTEGER NOT NULL DEFAULT 1,
                        is_system INTEGER NOT NULL DEFAULT 0,
                        item_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(parent_id) REFERENCES categories(id) ON DELETE CASCADE,
                        FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create indexes for category queries
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parent_id ON categories(parent_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_user_id ON categories(user_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_sort_order ON categories(sort_order)")

                // Insert default system categories
                val timestamp = System.currentTimeMillis()
                db.execSQL("""
                    INSERT INTO categories (name, description, icon, color, is_system, sort_order, created_at, updated_at)
                    VALUES
                    ('Uncategorized', 'Items without a specific category', '📋', '#9E9E9E', 1, 0, $timestamp, $timestamp),
                    ('Banking', 'Bank accounts and financial services', '🏦', '#4CAF50', 1, 1, $timestamp, $timestamp),
                    ('Email', 'Email accounts and services', '✉️', '#2196F3', 1, 2, $timestamp, $timestamp),
                    ('Social Media', 'Social networking accounts', '👥', '#E91E63', 1, 3, $timestamp, $timestamp),
                    ('Shopping', 'Online shopping and e-commerce', '🛒', '#FF9800', 1, 4, $timestamp, $timestamp),
                    ('Work', 'Work-related accounts and credentials', '💼', '#607D8B', 1, 5, $timestamp, $timestamp),
                    ('Entertainment', 'Streaming, gaming, and media services', '🎮', '#9C27B0', 1, 6, $timestamp, $timestamp),
                    ('Travel', 'Travel and booking services', '✈️', '#00BCD4', 1, 7, $timestamp, $timestamp)
                """.trimIndent())

                Timber.tag(TAG).i("✓ Migration 4 → 5 completed")
            }
        }

        /**
         * Migration 5 → 6: Add category foreign key to items table
         *
         * Changes:
         * - Adds category_id column (foreign key to categories)
         * - Renames category to category_name
         * - Maps existing category names to category IDs
         * - Sets default category for uncategorized items
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.tag(TAG).i("Running migration 5 → 6: Adding category_id to items")

                // Step 1: Create new items table with category_id
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        username TEXT,
                        encryptedPassword BLOB NOT NULL,
                        url TEXT,
                        notes TEXT,
                        category_id INTEGER,
                        category_name TEXT DEFAULT 'Uncategorized',
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastAccessedAt INTEGER,
                        FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
                        FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL ON UPDATE CASCADE
                    )
                """.trimIndent())

                // Step 2: Copy data from old table, mapping category names to IDs
                db.execSQL("""
                    INSERT INTO items_new (
                        id, userId, title, username, encryptedPassword, url, notes,
                        category_id, category_name, isFavorite, createdAt, updatedAt, lastAccessedAt
                    )
                    SELECT 
                        i.id, i.userId, i.title, i.username, i.encryptedPassword, i.url, i.notes,
                        c.id AS category_id,
                        COALESCE(i.category, 'Uncategorized') AS category_name,
                        i.isFavorite, i.createdAt, i.updatedAt, i.lastAccessedAt
                    FROM items i
                    LEFT JOIN categories c ON LOWER(c.name) = LOWER(COALESCE(i.category, 'Uncategorized'))
                """.trimIndent())

                // Step 3: Drop old table
                db.execSQL("DROP TABLE items")

                // Step 4: Rename new table
                db.execSQL("ALTER TABLE items_new RENAME TO items")

                // Step 5: Recreate indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_userId ON items(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_categoryId ON items(category_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_title ON items(title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_isFavorite ON items(isFavorite)")

                // Step 6: Update category item counts
                db.execSQL("""
                    UPDATE categories
                    SET item_count = (
                        SELECT COUNT(*)
                        FROM items
                        WHERE items.category_id = categories.id
                    ),
                    updated_at = ${System.currentTimeMillis()}
                """.trimIndent())

                Timber.tag(TAG).i("✓ Migration 5 → 6 completed")
            }
        }

        /**
         * ✅ NEW Migration 6 → 7: Add type field to items table
         *
         * Changes:
         * - Adds 'type' column for item classification (password, note, credit_card, etc.)
         * - Default value: 'password'
         * - Creates index on type for performance
         *
         * This migration enables:
         * - Item type filtering (show only passwords, notes, etc.)
         * - Better organization and categorization
         * - Future feature expansion (credit cards, secure notes, etc.)
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Timber.tag(TAG).i("Running migration 6 → 7: Adding 'type' field to items")

                try {
                    // Add 'type' column with default value 'password'
                    db.execSQL("ALTER TABLE items ADD COLUMN type TEXT NOT NULL DEFAULT 'password'")

                    // Create index on type column for efficient filtering
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_items_type ON items(type)")

                    Timber.tag(TAG).i("✓ Migration 6 → 7 completed successfully")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Migration 6 → 7 failed")
                    throw e
                }
            }
        }

        // ============================================
        // DATABASE CREATION METHODS
        // ============================================

        /**
         * Create database with encryption
         *
         * ✅ FIXED: Added @Suppress("DEPRECATION") for SupportFactory
         * ✅ FIXED: Removed .fallbackToDestructiveMigration()
         *
         * @param context Application context
         * @param passphrase Encryption passphrase (securely managed)
         * @return Configured AppDatabase instance
         */
        @Suppress("DEPRECATION")
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase, null, false)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "passbook_database"
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7  // ✅ Added new migration
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Timber.tag(TAG).i("Database onCreate callback")

                        // Enable WAL mode for better performance
                        db.execSQL("PRAGMA journal_mode=WAL")

                        // Enhanced SQLCipher security settings
                        db.execSQL("PRAGMA cipher_iterations=256000")
                        db.execSQL("PRAGMA cipher_memory_security=ON")
                        db.execSQL("PRAGMA cipher_plaintext_header_size=0")

                        // Enable foreign keys
                        db.execSQL("PRAGMA foreign_keys=ON")

                        // Secure delete to overwrite deleted data
                        db.execSQL("PRAGMA secure_delete=ON")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)

                        // Ensure security settings on every open
                        db.execSQL("PRAGMA foreign_keys=ON")
                        db.execSQL("PRAGMA secure_delete=ON")
                        db.execSQL("PRAGMA cipher_memory_security=ON")
                    }
                })
                // ✅ REMOVED: .fallbackToDestructiveMigration()
                // This is dangerous for production as it deletes all user data
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

        // ============================================
        // SINGLETON PATTERN
        // ============================================

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
