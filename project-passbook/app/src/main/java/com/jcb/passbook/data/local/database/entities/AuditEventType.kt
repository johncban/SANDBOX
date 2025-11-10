package com.jcb.passbook.data.local.database.entities


/**
 * Enum representing different types of audit events in the PassBook application
 * Used for security logging, monitoring, and compliance tracking
 */
enum class AuditEventType {
    // Authentication Events
    AUTHENTICATION_SUCCESS,
    AUTHENTICATION_FAILURE,
    LOGIN,
    LOGOUT,
    REGISTER,
    SESSION_START,
    SESSION_END,
    PASSWORD_CHANGE,
    BIOMETRIC_AUTH,

    // CRUD Operations
    CREATE,
    READ,
    UPDATE,
    DELETE,
    BULK_CREATE,
    BULK_UPDATE,
    BULK_DELETE,

    // Security Events
    WARNING,
    SECURITY_THREAT,
    KEY_ROTATION,
    BIOMETRIC_CHANGE,
    ROOT_DETECTION,
    DEBUGGER_DETECTION,
    EMULATOR_DETECTION,
    TAMPERING_DETECTED,
    INTEGRITY_CHECK_FAILED,
    UNAUTHORIZED_ACCESS,

    // System Events
    SYSTEM,
    CONFIGURATION_CHANGE,
    DATABASE_MIGRATION,
    APP_START,
    APP_STOP,
    ERROR,

    // Vault Operations
    VAULT_OPEN,
    VAULT_CLOSE,
    VAULT_LOCK,
    VAULT_UNLOCK,
    ITEM_VIEW,
    ITEM_EXPORT,
    ITEM_IMPORT,

    // Maintenance
    DATABASE_BACKUP,
    DATABASE_RESTORE,
    CACHE_CLEAR,
    DATA_WIPE
}