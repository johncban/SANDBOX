package com.jcb.passbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // Changed from Int to Long for consistency
    val username: String,
    val passwordHash: String, // Store password hash, not the actual password
    val biometricEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null,
    val failedLoginAttempts: Int = 0,
    val isLocked: Boolean = false,
    val lockUntil: Long? = null
)