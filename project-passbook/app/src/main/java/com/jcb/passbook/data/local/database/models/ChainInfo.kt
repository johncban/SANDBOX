package com.jcb.passbook.data.local.database.models

/**
 * Chain integrity information for audit log verification
 * Used by AuditDao to verify the integrity chain of audit entries
 */
data class ChainInfo(
    val id: Long,
    val chainPrevHash: String?,
    val chainHash: String?
)
