package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(private val userDao: UserDao) {

    // Existing methods
    suspend fun insert(user: User): Long = userDao.insert(user)

    suspend fun update(user: User) = userDao.update(user)

    suspend fun delete(user: User) = userDao.delete(user)

    fun getUser(id: Int): Flow<User?> = userDao.getUser(id)

    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)

    // NEW: Biometric methods to resolve UserViewModel compilation errors
    suspend fun findUserByBiometricTokenHash(tokenHash: ByteArray): User? =
        userDao.findUserByBiometricTokenHash(tokenHash)

    suspend fun updateUserBiometricToken(userId: Int, salt: ByteArray, hash: ByteArray) {
        userDao.updateUserBiometricToken(userId, salt, hash)
    }

    suspend fun clearUserBiometricToken(userId: Int) {
        userDao.clearUserBiometricToken(userId)
    }

    suspend fun isBiometricEnabledForUser(userId: Int): Boolean {
        return userDao.isBiometricEnabledForUser(userId) ?: false
    }

    suspend fun getUserIdByUsername(username: String): Int? =
        userDao.getUserIdByUsername(username)

    // Additional utility methods
    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()

    suspend fun getUserCount(): Int = userDao.getUserCount()

    // Method to create user and get ID (useful for registration with biometric setup)
    suspend fun insertAndGetId(user: User): Int = userDao.insert(user).toInt()
}