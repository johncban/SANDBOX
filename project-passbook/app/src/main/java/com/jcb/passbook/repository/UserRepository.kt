package com.jcb.passbook.repository

import com.jcb.passbook.room.User
import com.jcb.passbook.room.UserDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(private val userDao: UserDao) {

    suspend fun insert(user: User) = userDao.insert(user)

    suspend fun update(user: User) = userDao.update(user)

    suspend fun delete(user: User) = userDao.delete(user)

    fun getUser(id: Int): Flow<User> = userDao.getUser(id)

    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
}