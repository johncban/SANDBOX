package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.dao.ItemDao
import com.jcb.passbook.data.mappers.PasswordMapper
import com.jcb.passbook.domain.entities.Password
import com.jcb.passbook.domain.repository.PasswordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PasswordRepositoryImpl @Inject constructor(
    private val passwordDao: ItemDao,
    private val passwordMapper: PasswordMapper
) : PasswordRepository {

    override suspend fun savePassword(password: Password): Long {
        val entity = passwordMapper.toEntity(password.copy(updatedAt = System.currentTimeMillis()))
        return passwordDao.insertPassword(entity)
    }

    override suspend fun updatePassword(password: Password) {
        val entity = passwordMapper.toEntity(password.copy(updatedAt = System.currentTimeMillis()))
        passwordDao.updatePassword(entity)
    }

    override suspend fun deletePassword(passwordId: Long, userId: Long) {
        val entity = passwordDao.getPasswordById(passwordId, userId)
        entity?.let { passwordDao.deletePassword(it) }
    }

    override fun getPasswordsForUser(userId: Long): Flow<List<Password>> {
        return passwordDao.getPasswordsForUser(userId)
            .map { entities -> entities.map { passwordMapper.toDomain(it) } }
    }

    override suspend fun getPasswordById(id: Long, userId: Long): Password? {
        return passwordDao.getPasswordById(id, userId)?.let { passwordMapper.toDomain(it) }
    }

    override fun getFavoritePasswords(userId: Long): Flow<List<Password>> {
        return passwordDao.getFavoritePasswords(userId)
            .map { entities -> entities.map { passwordMapper.toDomain(it) } }
    }

    override fun searchPasswords(userId: Long, query: String): Flow<List<Password>> {
        return passwordDao.searchPasswords(userId, "%$query%")
            .map { entities -> entities.map { passwordMapper.toDomain(it) } }
    }

    override suspend fun deleteAllPasswordsForUser(userId: Long) {
        passwordDao.deleteAllPasswordsForUser(userId)
    }

    override suspend fun getPasswordCountForUser(userId: Long): Int {
        return passwordDao.getPasswordCountForUser(userId)
    }
}
