package com.jcb.passbook.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

import net.sqlcipher.database.SupportFactory

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
