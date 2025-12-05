package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow

/**
 * ✅ FIXED: Added uniqueness enforcement and better error handling
 * - Username uniqueness enforced at DB level
 * - OnConflictStrategy.ABORT throws exception for duplicate users
 * - Clear contracts for single vs multiple user returns
 */
@Dao
interface UserDao {

    /**
     * ✅ Insert user with ABORT strategy - throws on duplicate username
     * @return userId (primary key) of inserted user
     * @throws android.database.sqlite.SQLiteConstraintException if username exists
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    /**
     * ✅ Get user by ID - returns null if not found
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUser(userId: Long): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserFlow(userId: Long): Flow<User?>

    /**
     * ✅ CRITICAL: Username lookup must return single user or null
     * Database schema MUST have UNIQUE constraint on username column
     * @return User if found, null otherwise
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE username = :username")
    fun getUserByUsernameFlow(username: String): Flow<User?>

    /**
     * ✅ Get currently active (logged-in) user
     * Only one user should be active at a time (enforced in ViewModel)
     */
    @Query("SELECT * FROM users WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveUser(): User?

    @Query("SELECT * FROM users WHERE is_active = 1 LIMIT 1")
    fun getActiveUserFlow(): Flow<User?>

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("UPDATE users SET last_login_at = :timestamp WHERE id = :userId")
    suspend fun updateLastLogin(userId: Long, timestamp: Long)

    /**
     * ✅ NEW: Clear active status for all users (single-session enforcement)
     */
    @Query("UPDATE users SET is_active = 0")
    suspend fun clearAllActiveSessions()

    /**
     * ✅ NEW: Set specific user as active
     */
    @Query("UPDATE users SET is_active = 1 WHERE id = :userId")
    suspend fun setUserActive(userId: Long)
}
