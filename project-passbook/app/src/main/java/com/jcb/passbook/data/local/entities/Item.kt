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
    val id: Int = 0,
    val name: String,
    val encryptedPasswordData: ByteArray,
    val userId: Int // Foreign key linking to the User table
) {
    override fun equals(other: Any?): Boolean = other is Item &&
            id == other.id &&
            name == other.name &&
            encryptedPasswordData.contentEquals(other.encryptedPasswordData) &&
            userId == other.userId

    override fun hashCode(): Int = 31 * id + name.hashCode() +
            encryptedPasswordData.contentHashCode() + userId
}