package com.jcb.passbook.data.local.database.entities

enum class AuditOutcome(val value: String) {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    WARNING("WARNING"),
    BLOCKED("BLOCKED")
}