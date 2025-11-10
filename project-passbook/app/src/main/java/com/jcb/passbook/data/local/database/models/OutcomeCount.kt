package com.jcb.passbook.data.local.database.models

/**
 * Outcome count statistics
 * Used by AuditDao to aggregate and count audit events by outcome (success/failure/blocked)
 */
data class OutcomeCount(
    val outcome: String,
    val count: Int
)
