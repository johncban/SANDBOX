package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)")
    suspend fun userExists(username: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    suspend fun emailExists(email: String): Boolean

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    // FIXED: Use created_at (the actual column name in the database)
    @Query("SELECT * FROM users ORDER BY created_at DESC")
    fun getAllUsersSortedByDate(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username LIKE '%' || :searchQuery || '%' OR email LIKE '%' || :searchQuery || '%'")
    fun searchUsers(searchQuery: String): Flow<List<User>>

    // FIXED: Use created_at (the actual column name in the database)
    @Query("SELECT * FROM users WHERE created_at >= :startDate AND created_at <= :endDate")
    suspend fun getUsersCreatedBetween(startDate: Long, endDate: Long): List<User>

    @Query("UPDATE users SET password_hash = :newPassword WHERE id = :userId")
    suspend fun updatePassword(userId: Long, newPassword: String)

    @Query("SELECT * FROM users WHERE id IN (:userIds)")
    suspend fun getUsersByIds(userIds: List<Long>): List<User>
}
