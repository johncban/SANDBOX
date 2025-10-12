package com.jcb.passbook.domain.usecases.security

import com.jcb.passbook.domain.repository.AuditRepository
import com.jcb.passbook.domain.entities.AuditEntry
import com.jcb.passbook.domain.entities.AuditEventType
import com.jcb.passbook.domain.entities.AuditOutcome
import javax.inject.Inject

class ValidateBiometricUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(userId: Long, isSuccessful: Boolean): Result<Unit> {
        return try {
            auditRepository.logEvent(
                AuditEntry(
                    userId = userId,
                    eventType = AuditEventType.LOGIN,
                    action = if (isSuccessful) "Biometric authentication successful" else "Biometric authentication failed",
                    outcome = if (isSuccessful) AuditOutcome.SUCCESS else AuditOutcome.FAILURE
                )
            )

            if (isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Biometric authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
