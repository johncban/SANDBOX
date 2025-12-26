// File: app/src/main/java/com/jcb/passbook/data/local/database/dao/UserDao.kt
package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow

/**
 * UserDao - Data Access Object for User operations
 *
 * REFACTORED: Removed duplicate method signatures
 * All methods now have unique names and purposes
 */
@Dao
interface UserDao {

    // ============================================
    // CREATE Operations
    // ============================================

    /**
     * Insert a new user
     * @return The ID of the inserted user
     * @throws SQLiteConstraintException if username already exists
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    // ============================================
    // READ Operations
    // ============================================

    /**
     * Get user by ID (one-time query)
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?

    /**
     * Get user by ID as reactive Flow
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserFlow(userId: Long): Flow<User?>

    /**
     * Get user by username (one-time query)
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    /**
     * Get user by username as reactive Flow
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    fun getUserByUsernameFlow(username: String): Flow<User?>

    /**
     * Get the currently active user as reactive Flow
     */
    @Query("SELECT * FROM users WHERE is_active = 1 LIMIT 1")
    fun getActiveUserFlow(): Flow<User?>

    /**
     * Get all users (active and inactive) as reactive Flow
     */
    @Query("SELECT * FROM users ORDER BY created_at DESC")
    fun getAllUsers(): Flow<List<User>>

    /**
     * Get all active users as reactive Flow
     */
    @Query("SELECT * FROM users WHERE is_active = 1 ORDER BY username ASC")
    fun getAllActiveUsers(): Flow<List<User>>

    /**
     * Get total user count
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    /**
     * Get active user count
     */
    @Query("SELECT COUNT(*) FROM users WHERE is_active = 1")
    suspend fun getActiveUserCount(): Int

    // ============================================
    // UPDATE Operations
    // ============================================

    /**
     * Update user entity
     */
    @Update
    suspend fun update(user: User)

    /**
     * Update last login timestamp
     */
    @Query("UPDATE users SET last_login_at = :timestamp WHERE id = :userId")
    suspend fun updateLastLogin(userId: Long, timestamp: Long)

    /**
     * Update user active status
     */
    @Query("UPDATE users SET is_active = :isActive WHERE id = :userId")
    suspend fun updateActiveStatus(userId: Long, isActive: Boolean)

    /**
     * Update user password hash and salt
     */
    @Query("UPDATE users SET password_hash = :passwordHash, salt = :salt WHERE id = :userId")
    suspend fun updatePassword(userId: Long, passwordHash: ByteArray, salt: ByteArray)

    // ============================================
    // DELETE Operations
    // ============================================

    /**
     * Delete user entity
     * WARNING: Will cascade delete all user's items due to foreign key constraint
     */
    @Delete
    suspend fun delete(user: User)

    /**
     * Delete user by ID
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: Long)

    /**
     * Soft delete - deactivate user instead of deleting
     */
    @Query("UPDATE users SET is_active = 0 WHERE id = :userId")
    suspend fun softDelete(userId: Long)
}
