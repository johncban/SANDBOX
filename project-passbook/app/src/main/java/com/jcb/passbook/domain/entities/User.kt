package com.jcb.passbook.domain.entities

data class User(
    val id: Long = 0,
    val username: String,
    val biometricEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null,
    val failedLoginAttempts: Int = 0,
    val isLocked: Boolean = false,
    val lockUntil: Long? = null
) {
    fun isAccountLocked(): Boolean {
        return isLocked && (lockUntil == null || System.currentTimeMillis() < lockUntil)
    }

    fun canAttemptLogin(): Boolean {
        return !isAccountLocked()
    }
}
