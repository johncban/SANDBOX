package com.jcb.passbook.domain.entities

data class SecuritySettings(
    val biometricEnabled: Boolean = false,
    val autoLockTimeout: Long = 300000L, // 5 minutes
    val failedAttemptsLimit: Int = 5,
    val requirePasswordOnLaunch: Boolean = true,
    val enableAuditLogging: Boolean = true,
    val autoBackup: Boolean = false,
    val clipboardClearTimeout: Long = 30000L // 30 seconds
)
