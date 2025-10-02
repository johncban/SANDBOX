package com.jcb.passbook.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    // val passwordHash: ByteArray // Store password hash, not the actual password
    val passwordHash: String
)
