package com.jcb.passbook.domain.usecases.security

import com.jcb.passbook.domain.entities.User
import com.jcb.passbook.domain.repository.UserRepository
import com.jcb.passbook.domain.repository.AuditRepository
import com.jcb.passbook.domain.entities.AuditEntry
import com.jcb.passbook.domain.entities.AuditEventType
import com.jcb.passbook.domain.entities.AuditOutcome
import javax.inject.Inject

class AuthenticateUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(username: String, password: String): Result<User> {
        return try {
            val user = userRepository.authenticateUser(username, password)

            if (user != null) {
                userRepository.updateLastLogin(user.id)
                userRepository.resetFailedAttempts(user.id)

                auditRepository.logEvent(
                    AuditEntry(
                        userId = user.id,
                        username = username,
                        eventType = AuditEventType.LOGIN,
                        action = "User login successful",
                        outcome = AuditOutcome.SUCCESS
                    )
                )

                Result.success(user)
            } else {
                // Check if user exists to increment failed attempts
                val existingUser = userRepository.getUserByUsername(username)
                existingUser?.let {
                    userRepository.incrementFailedAttempts(it.id)
                }

                auditRepository.logEvent(
                    AuditEntry(
                        username = username,
                        eventType = AuditEventType.AUTHENTICATION_FAILURE,
                        action = "Failed login attempt",
                        outcome = AuditOutcome.FAILURE
                    )
                )

                Result.failure(SecurityException("Authentication failed"))
            }
        } catch (e: Exception) {
            auditRepository.logEvent(
                AuditEntry(
                    username = username,
                    eventType = AuditEventType.AUTHENTICATION_FAILURE,
                    action = "Login error: ${e.message}",
                    outcome = AuditOutcome.FAILURE,
                    errorMessage = e.message
                )
            )
            Result.failure(e)
        }
    }
}
