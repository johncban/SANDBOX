
package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

/**
 * User Entity - Represents application users
 *
 * This entity is referenced by Item and AuditEntry through foreign keys
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true, name = "index_users_username")
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "passwordHash")
    val passwordHash: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "lastLoginAt")
    val lastLoginAt: Long? = null,

    @ColumnInfo(name = "isActive")
    val isActive: Boolean = true
)