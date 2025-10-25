package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUser(id: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    // NEW: Biometric-related queries
    @Query("SELECT * FROM users WHERE biometric_token_hash = :tokenHash AND biometric_enabled = 1 LIMIT 1")
    suspend fun findUserByBiometricTokenHash(tokenHash: ByteArray): User?

    @Query("UPDATE users SET biometric_token_salt = :salt, biometric_token_hash = :hash, biometric_enabled = 1, biometric_setup_at = :setupTime WHERE id = :userId")
    suspend fun updateUserBiometricToken(userId: Int, salt: ByteArray, hash: ByteArray, setupTime: Long = System.currentTimeMillis())

    @Query("UPDATE users SET biometric_token_salt = NULL, biometric_token_hash = NULL, biometric_enabled = 0, biometric_setup_at = NULL WHERE id = :userId")
    suspend fun clearUserBiometricToken(userId: Int)

    @Query("SELECT biometric_enabled FROM users WHERE id = :userId")
    suspend fun isBiometricEnabledForUser(userId: Int): Boolean?

    @Query("SELECT id FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserIdByUsername(username: String): Int?

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}