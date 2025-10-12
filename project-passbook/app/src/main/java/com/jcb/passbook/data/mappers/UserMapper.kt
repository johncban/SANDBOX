package com.jcb.passbook.data.mappers

import com.jcb.passbook.data.local.entities.User
import com.jcb.passbook.domain.entities.User
import javax.inject.Inject

class UserMapper @Inject constructor() {

    fun toDomain(entity: User): User {
        return User(
            id = entity.id,
            username = entity.username,
            biometricEnabled = entity.biometricEnabled,
            createdAt = entity.createdAt,
            lastLoginAt = entity.lastLoginAt,
            failedLoginAttempts = entity.failedLoginAttempts,
            isLocked = entity.isLocked,
            lockUntil = entity.lockUntil
        )
    }

    fun toEntity(domain: User, passwordHash: String, salt: String): UserEntity {
        return UserEntity(
            id = domain.id,
            username = domain.username,
            passwordHash = passwordHash,
            salt = salt,
            biometricEnabled = domain.biometricEnabled,
            createdAt = domain.createdAt,
            lastLoginAt = domain.lastLoginAt,
            failedLoginAttempts = domain.failedLoginAttempts,
            isLocked = domain.isLocked,
            lockUntil = domain.lockUntil
        )
    }
}
