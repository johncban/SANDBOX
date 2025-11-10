package com.jcb.passbook.data.local.database.models

/**
 * Event type count statistics
 * Used by AuditDao to aggregate and count audit events by type
 */
data class EventTypeCount(
    val eventType: String,
    val count: Int
)
