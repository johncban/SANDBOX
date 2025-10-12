package com.jcb.passbook.data.local.entities

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
