package com.jcb.passbook.data.local.entities

@Entity
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    // val passwordHash: ByteArray // Store password hash, not the actual password
    val passwordHash: String
)