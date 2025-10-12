package com.jcb.passbook.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.jcb.passbook.data.local.dao.*
import com.jcb.passbook.data.local.entities.*
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        Item::class,
        User::class,
        Audit::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class PassbookDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val DATABASE_NAME = "passbook_database"

        fun create(context: Context, passphrase: ByteArray): PassbookDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(
                context,
                PassbookDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
