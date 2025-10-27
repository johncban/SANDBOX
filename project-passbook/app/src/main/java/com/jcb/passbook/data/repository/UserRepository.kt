package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(private val userDao: UserDao) {

    suspend fun insert(user: User) = userDao.insert(user)

    suspend fun update(user: User) = userDao.update(user)

    suspend fun delete(user: User) = userDao.delete(user)

    fun getUser(id: Int): Flow<User> = userDao.getUser(id)

    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
}