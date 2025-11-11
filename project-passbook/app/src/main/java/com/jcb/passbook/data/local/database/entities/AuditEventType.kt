package com.jcb.passbook.data.local.database.entities

enum class AuditEventType {
    // Authentication & User
    LOGIN,
    LOGOUT,
    AUTHENTICATION_FAILURE,
    REGISTER,

    // CRUD Operations
    CREATE,
    READ,
    UPDATE,
    DELETE,

    // Security Events
    WARNING,
    SECURITY_BREACH,
    ACCESS_DENIED,

    // Encryption Operations
    ENCRYPT,
    DECRYPT,

    // Biometric
    BIOMETRIC_AUTH,

    // Database
    DATABASE_CHANGE,

    // Other
    SYSTEM_START,
    SYSTEM_STOP
}
