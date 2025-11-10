package com.jcb.passbook.data.local.database.models

/**
 * Security level count statistics
 * Used by AuditDao to aggregate and count audit events by security level (normal/elevated/critical)
 */
data class SecurityLevelCount(
    val securityLevel: String,
    val count: Int
)
