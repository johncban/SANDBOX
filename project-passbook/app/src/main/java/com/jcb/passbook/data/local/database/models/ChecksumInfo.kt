package com.jcb.passbook.data.local.database.models

/**
 * Checksum information for audit entry integrity verification
 * Used by AuditDao to verify the integrity of individual audit entries
 */
data class ChecksumInfo(
    val id: Long,
    val checksum: String?
)
