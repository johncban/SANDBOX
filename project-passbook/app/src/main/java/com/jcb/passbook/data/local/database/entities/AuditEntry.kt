package com.jcb.passbook.data.local.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_entries",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["eventType"]),
        Index(value = ["timestamp"]),
        Index(value = ["outcome"])
    ]
)
data class AuditEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Int? = null,
    val eventType: String,
    val outcome: String,
    val eventDescription: String,
    val username: String? = null,
    val sessionId: String? = null,
    val metadata: String? = null,
    val timestamp: Long,
    val checksum: String? = null
)