package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val username: String,
    val passwordHash: String,
    val salt: ByteArray,
    val createdAt: Long,
    val lastLogin: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (username != other.username) return false
        if (passwordHash != other.passwordHash) return false
        if (!salt.contentEquals(other.salt)) return false
        if (createdAt != other.createdAt) return false
        if (lastLogin != other.lastLogin) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + username.hashCode()
        result = 31 * result + passwordHash.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastLogin.hashCode()
        return result
    }
}