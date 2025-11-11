package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    // Returns Flow<User?> to match UserDao
    fun getActiveUser(): Flow<User?> {
        return userDao.getActiveUserFlow()
    }

    // Gets user by username and returns nullable User
    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserByUsernameFlow(username).firstOrNull()
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

    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }
}
