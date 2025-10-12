package com.jcb.passbook.domain.repository

import com.jcb.passbook.domain.entities.Password
import kotlinx.coroutines.flow.Flow

interface PasswordRepository {

    suspend fun savePassword(password: Password): Long

    suspend fun updatePassword(password: Password)

    suspend fun deletePassword(passwordId: Long, userId: Long)

    fun getPasswordsForUser(userId: Long): Flow<List<Password>>

    suspend fun getPasswordById(id: Long, userId: Long): Password?

    fun getFavoritePasswords(userId: Long): Flow<List<Password>>

    fun searchPasswords(userId: Long, query: String): Flow<List<Password>>

    suspend fun deleteAllPasswordsForUser(userId: Long)

    suspend fun getPasswordCountForUser(userId: Long): Int
}
