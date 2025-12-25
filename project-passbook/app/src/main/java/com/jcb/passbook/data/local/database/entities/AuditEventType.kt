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
    CREATE_ITEM,        // ✅ ADDED
    UPDATE_ITEM,        // ✅ ADDED
    DELETE_ITEM,        // ✅ ADDED
    VIEW_ITEM,          // ✅ ADDED

    // Security Events
    WARNING,
    SECURITY_BREACH,
    ACCESS_DENIED,
    SECURITY_EVENT,     // ✅ ADDED
    BLOCKED,            // ✅ ADDED

    // Encryption Operations
    ENCRYPT,
    DECRYPT,
    KEY_ROTATION,       // ✅ ADDED

    // Biometric
    BIOMETRIC_AUTH,

    // Database
    DATABASE_CHANGE,

    // Other
    SYSTEM_START,
    SYSTEM_STOP,
    SYSTEM_EVENT,        // ✅ ADDED
    PASSWORD_CHANGE,
    DATABASE_KEY_ACCESS
}
