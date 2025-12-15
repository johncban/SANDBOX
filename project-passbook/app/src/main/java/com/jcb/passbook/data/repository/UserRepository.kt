package com.jcb.passbook.data.repositories

import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.datastore.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val userPreferences: UserPreferences
) {

    fun getCurrentUser(): Flow<User?> = flow {
        val userId = userPreferences.currentUserId.first()
        if (userId != -1) {
            val user = userDao.getUserById(userId.toLong())
            emit(user)
        } else {
            emit(null)
        }
    }

    suspend fun authenticateUser(username: String, passwordHash: String): User? {
        val user = userDao.getUserByUsername(username)
        return if (user != null && user.passwordHash == passwordHash) {
            userPreferences.setCurrentUserId(user.id.toInt())
            user
        } else {
            null
        }
    }

    suspend fun clearCurrentUser() {
        userPreferences.clearCurrentUserId()
    }

    suspend fun insertUser(user: User): Long {
        return userDao.insert(user)
    }

    suspend fun updateUser(user: User) {
        userDao.update(user)
    }

    suspend fun deleteUser(user: User) {
        userDao.delete(user)
    }

    // ✅ NEW: Set current user ID in preferences
    suspend fun setCurrentUserId(userId: Int) {
        userPreferences.setCurrentUserId(userId)
    }

    // ✅ NEW: Get user by username (for duplicate check)
    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserByUsername(username)
    }
}
