package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo
import java.util.Date

/**
 * Item Entity - Represents a password/credential entry in the database
 *
 * FIXES APPLIED:
 * - Added Index on userId foreign key column to prevent full table scans
 * - Proper foreign key cascade behavior for data integrity
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,  // Delete items when user is deleted
            onUpdate = ForeignKey.CASCADE    // Update items when user ID changes
        )
    ],
    indices = [
        Index(value = ["userId"], name = "index_items_userId"),  // CRITICAL FIX: Prevents full table scans
        Index(value = ["title"], name = "index_items_title"),     // Performance: Search by title
        Index(value = ["category"], name = "index_items_category") // Performance: Filter by category
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "userId")
    val userId: Int,  // Foreign key to User table (now properly indexed)

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "username")
    val username: String? = null,

    @ColumnInfo(name = "encryptedPassword")
    val encryptedPassword: ByteArray,  // Encrypted password data

    @ColumnInfo(name = "url")
    val url: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "category")
    val category: String = "General",

    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "lastAccessedAt")
    val lastAccessedAt: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Item

        if (id != other.id) return false
        if (userId != other.userId) return false
        if (title != other.title) return false
        if (!encryptedPassword.contentEquals(other.encryptedPassword)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + userId
        result = 31 * result + title.hashCode()
        result = 31 * result + encryptedPassword.contentHashCode()
        return result
    }
}