package com.jcb.passbook.domain.usecases.password

import com.jcb.passbook.domain.entities.Password
import com.jcb.passbook.domain.repository.PasswordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPasswordsUseCase @Inject constructor(
    private val passwordRepository: PasswordRepository
) {
    operator fun invoke(userId: Long): Flow<List<Password>> {
        return passwordRepository.getPasswordsForUser(userId)
    }
}
