package com.jcb.passbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.jcb.passbook.data.local.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM user WHERE id = :id")
    fun getUser(id: Long): Flow<User?>

    @Query("SELECT * FROM user WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM user")
    fun getAllUsers(): Flow<List<User>>

    @Query("DELETE FROM user WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Authentication and security methods
    @Query("UPDATE user SET lastLoginAt = :timestamp WHERE id = :userId")
    suspend fun updateLastLogin(userId: Long, timestamp: Long)

    @Query("UPDATE user SET failedLoginAttempts = 0 WHERE id = :userId")
    suspend fun resetFailedAttempts(userId: Long)

    @Query("UPDATE user SET failedLoginAttempts = failedLoginAttempts + 1 WHERE id = :userId")
    suspend fun incrementFailedAttempts(userId: Long)

    @Query("UPDATE user SET isLocked = :isLocked, lockUntil = :lockUntil WHERE id = :userId")
    suspend fun setUserLocked(userId: Long, isLocked: Boolean, lockUntil: Long?)

    @Query("SELECT * FROM user WHERE username = :username AND passwordHash = :passwordHash")
    suspend fun authenticateUser(username: String, passwordHash: String): User?

    @Query("SELECT failedLoginAttempts FROM user WHERE id = :userId")
    suspend fun getFailedAttempts(userId: Long): Int?
}