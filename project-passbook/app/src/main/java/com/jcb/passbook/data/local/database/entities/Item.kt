package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(foreignKeys = [ForeignKey(
    entity = User::class,
    parentColumns = ["id"],
    childColumns = ["userId"],
    onDelete = ForeignKey.CASCADE
    )]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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




/***
package com.jcb.passbook.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(foreignKeys = [ForeignKey(
    entity = User::class,
    parentColumns = ["id"],
    childColumns = ["userId"],
    onDelete = ForeignKey.CASCADE
)])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    // Removed @TypeConverters(PasswordConverter::class)
    // Stores the combined IV + encrypted password data
    val encryptedPasswordData: ByteArray,
    val userId: Int // Foreign key linking to the User table (no default needed if always set)
) {
    // Auto-generated equals/hashCode by data class might be sufficient,
    // but explicitly implementing for ByteArray comparison is safer.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Item
        if (id != other.id) return false
        if (name != other.name) return false
        // Compare content of the byte array
        if (!encryptedPasswordData.contentEquals(other.encryptedPasswordData)) return false
        if (userId != other.userId) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        // Use contentHashCode for byte array
        result = 31 * result + encryptedPasswordData.contentHashCode()
        result = 31 * result + userId
        return result
    }
}
***/