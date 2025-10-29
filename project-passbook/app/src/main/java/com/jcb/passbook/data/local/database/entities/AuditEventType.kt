package com.jcb.passbook.data.local.database.entities

enum class AuditEventType(val value: String) {
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT"),
    REGISTER("REGISTER"),
    CREATE("CREATE"),
    READ("READ"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    SECURITY_EVENT("SECURITY_EVENT"),
    SYSTEM_EVENT("SYSTEM_EVENT"),
    KEY_ROTATION("KEY_ROTATION"),
    AUTHENTICATION_FAILURE("AUTH_FAILURE")
}