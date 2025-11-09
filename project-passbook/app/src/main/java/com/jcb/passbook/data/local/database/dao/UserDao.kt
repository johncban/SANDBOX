
package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow

/**
 * UserDao - Data Access Object for User entity
 */
@Dao
interface UserDao {

    @Insert
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUser(userId: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE username = :username")
    fun getUserByUsername(username: String): Flow<User?>

    @Query("SELECT * FROM users WHERE isActive = 1")
    fun getAllActiveUsers(): Flow<List<User>>

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE id = :userId")
    suspend fun updateLastLogin(userId: Int, timestamp: Long)

    @Query("UPDATE users SET isActive = :isActive WHERE id = :userId")
    suspend fun setUserActive(userId: Int, isActive: Boolean)
}