package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]), // CRITICAL: Required for foreign key
        Index(value = ["title"]),
        Index(value = ["type"])
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val title: String,
    val username: String?,
    val password: String?, // This will be encrypted
    val url: String?,
    val notes: String?,
    val type: String, // e.g., "login", "note", "card"
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList()
)