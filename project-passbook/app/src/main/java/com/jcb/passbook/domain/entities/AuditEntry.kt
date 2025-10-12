package com.jcb.passbook.domain.entities

data class AuditEntry(
    val id: Long = 0,
    val userId: Long? = null,
    val username: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: AuditEventType,
    val action: String,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val deviceInfo: String? = null,
    val appVersion: String? = null,
    val sessionId: String? = null,
    val outcome: AuditOutcome,
    val errorMessage: String? = null,
    val securityLevel: SecurityLevel = SecurityLevel.NORMAL
)

enum class AuditEventType {
    LOGIN, LOGOUT, REGISTER, CREATE, READ, UPDATE, DELETE,
    SECURITY_EVENT, SYSTEM_EVENT, KEY_ROTATION, AUTHENTICATION_FAILURE
}

enum class AuditOutcome {
    SUCCESS, FAILURE, WARNING, BLOCKED
}

enum class SecurityLevel {
    NORMAL, ELEVATED, CRITICAL
}
