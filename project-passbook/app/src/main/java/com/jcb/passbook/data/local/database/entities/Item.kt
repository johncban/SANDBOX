package com.jcb.passbook.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Item - Password entry entity
 *
 * SECURITY:
 * - encryptedPassword stored as ByteArray (encrypted via CryptoManager)
 * - passwordCategory uses PasswordCategoryEnum (stored as String)
 *
 * RELATIONSHIPS:
 * - Foreign key to User (CASCADE delete)
 * - Optional foreign key to Category (SET NULL on delete)
 */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["category_id"]),
        Index(value = ["password_category"]),
        Index(value = ["isFavorite"]),
        Index(value = ["createdAt"])
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "userId")
    val userId: Long,

    val title: String,
    val username: String? = null,

    @ColumnInfo(name = "encryptedPassword")
    val encryptedPassword: ByteArray,

    val url: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "category_name")
    val categoryName: String? = null,

    @ColumnInfo(name = "password_category")
    val passwordCategory: String = PasswordCategoryEnum.OTHER.name,

    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long? = null
) {
    /**
     * Convert stored string to enum for type-safe usage
     */
    fun getPasswordCategoryEnum(): PasswordCategoryEnum {
        return PasswordCategoryEnum.fromString(passwordCategory)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Item

        if (id != other.id) return false
        if (userId != other.userId) return false
        if (title != other.title) return false
        if (username != other.username) return false
        if (!encryptedPassword.contentEquals(other.encryptedPassword)) return false
        if (url != other.url) return false
        if (notes != other.notes) return false
        if (categoryId != other.categoryId) return false
        if (categoryName != other.categoryName) return false
        if (passwordCategory != other.passwordCategory) return false
        if (isFavorite != other.isFavorite) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (lastAccessedAt != other.lastAccessedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + encryptedPassword.contentHashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (notes?.hashCode() ?: 0)
        result = 31 * result + (categoryId?.hashCode() ?: 0)
        result = 31 * result + (categoryName?.hashCode() ?: 0)
        result = 31 * result + passwordCategory.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (lastAccessedAt?.hashCode() ?: 0)
        return result
    }
}
