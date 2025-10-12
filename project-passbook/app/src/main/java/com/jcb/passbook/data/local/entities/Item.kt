package com.jcb.passbook.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId"], name = "index_item_userId")]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // Changed from Int to Long for consistency
    val name: String,
    val username: String? = null,
    val encryptedPasswordData: ByteArray, // Encrypted password
    val website: String? = null,
    val notes: String? = null,
    val categoryId: String? = null,
    val userId: Long, // Changed from Int to Long for consistency
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val tags: String? = null // JSON string of tags
) {
    override fun equals(other: Any?): Boolean = other is Item &&
            id == other.id &&
            name == other.name &&
            username == other.username &&
            encryptedPasswordData.contentEquals(other.encryptedPasswordData) &&
            website == other.website &&
            notes == other.notes &&
            categoryId == other.categoryId &&
            userId == other.userId &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            isFavorite == other.isFavorite &&
            tags == other.tags

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + encryptedPasswordData.contentHashCode()
        result = 31 * result + (website?.hashCode() ?: 0)
        result = 31 * result + (notes?.hashCode() ?: 0)
        result = 31 * result + (categoryId?.hashCode() ?: 0)
        result = 31 * result + userId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + (tags?.hashCode() ?: 0)
        return result
    }
}