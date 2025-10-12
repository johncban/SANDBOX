package com.jcb.passbook.domain.usecases.password

import com.jcb.passbook.domain.entities.Password
import com.jcb.passbook.domain.repository.PasswordRepository
import com.jcb.passbook.domain.repository.AuditRepository
import com.jcb.passbook.domain.entities.AuditEntry
import com.jcb.passbook.domain.entities.AuditEventType
import com.jcb.passbook.domain.entities.AuditOutcome
import javax.inject.Inject

class SavePasswordUseCase @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(password: Password): Result<Long> {
        return try {
            if (!password.isValid()) {
                return Result.failure(IllegalArgumentException("Invalid password data"))
            }

            val passwordId = passwordRepository.savePassword(password)

            // Log audit event
            auditRepository.logEvent(
                AuditEntry(
                    userId = password.userId,
                    eventType = AuditEventType.CREATE,
                    action = "Password created: ${password.name}",
                    resourceType = "PASSWORD",
                    resourceId = passwordId.toString(),
                    outcome = AuditOutcome.SUCCESS
                )
            )

            Result.success(passwordId)
        } catch (e: Exception) {
            auditRepository.logEvent(
                AuditEntry(
                    userId = password.userId,
                    eventType = AuditEventType.CREATE,
                    action = "Failed to create password: ${password.name}",
                    resourceType = "PASSWORD",
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.message
                )
            )
            Result.failure(e)
        }
    }
}
