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

    @Query("SELECT * FROM User WHERE id = :id")
    fun getUser(id: Int): Flow<User?>

    @Query("SELECT * FROM User WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM User")
    fun getAllUsers(): Flow<List<User>>

    @Query("DELETE FROM User WHERE id = :id")
    suspend fun deleteById(id: Int)
}