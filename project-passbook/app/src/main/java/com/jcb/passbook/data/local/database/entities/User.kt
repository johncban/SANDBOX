package com.jcb.passbook.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ✅ CRITICAL FIX: Added UNIQUE index on username
 * - Prevents duplicate usernames at database level
 * - Database will throw SQLiteConstraintException on duplicate insert
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]  // ✅ Enforces uniqueness
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    @ColumnInfo(name = "salt")
    val salt: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_login_at")
    val lastLoginAt: Long? = null
)
