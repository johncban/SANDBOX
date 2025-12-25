package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true, name = "index_users_username")
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,  // ✅ CHANGED: Int -> Long

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "password_hash")
    val passwordHash: ByteArray,  // ✅ CHANGED: String -> ByteArray

    @ColumnInfo(name = "salt")
    val salt: ByteArray,  // ✅ ADDED

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_login_at")
    val lastLoginAt: Long? = null,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as User
        if (id != other.id) return false
        if (username != other.username) return false
        if (!passwordHash.contentEquals(other.passwordHash)) return false
        if (!salt.contentEquals(other.salt)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordHash.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }
}
