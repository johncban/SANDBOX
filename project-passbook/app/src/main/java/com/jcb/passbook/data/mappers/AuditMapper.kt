package com.jcb.passbook.data.mappers

import com.jcb.passbook.data.local.entities.Audit
import com.jcb.passbook.domain.entities.AuditEntry
import com.jcb.passbook.domain.entities.AuditEventType
import com.jcb.passbook.domain.entities.AuditOutcome
import com.jcb.passbook.domain.entities.SecurityLevel
import javax.inject.Inject

class AuditMapper @Inject constructor() {

    fun toDomain(entity: Audit): AuditEntry {
        return AuditEntry(
            id = entity.id,
            userId = entity.userId,
            username = entity.username,
            timestamp = entity.timestamp,
            eventType = AuditEventType.valueOf(entity.eventType),
            action = entity.action,
            resourceType = entity.resourceType,
            resourceId = entity.resourceId,
            deviceInfo = entity.deviceInfo,
            appVersion = entity.appVersion,
            sessionId = entity.sessionId,
            outcome = AuditOutcome.valueOf(entity.outcome),
            errorMessage = entity.errorMessage,
            securityLevel = SecurityLevel.valueOf(entity.securityLevel)
        )
    }

    fun toEntity(domain: AuditEntry): Audit {
        return Audit(
            id = domain.id,
            userId = domain.userId,
            username = domain.username,
            timestamp = domain.timestamp,
            eventType = domain.eventType.name,
            action = domain.action,
            resourceType = domain.resourceType,
            resourceId = domain.resourceId,
            deviceInfo = domain.deviceInfo,
            appVersion = domain.appVersion,
            sessionId = domain.sessionId,
            outcome = domain.outcome.name,
            errorMessage = domain.errorMessage,
            securityLevel = domain.securityLevel.name,
            checksum = generateChecksum(domain)
        )
    }

    private fun generateChecksum(entry: AuditEntry): String {
        val data = "${entry.userId}${entry.timestamp}${entry.eventType}${entry.action}${entry.resourceType}${entry.resourceId}${entry.outcome}"
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
