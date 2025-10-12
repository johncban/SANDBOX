package com.jcb.passbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.jcb.passbook.data.local.entities.Item

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM item WHERE userId = :userId")
    fun getItemsForUser(userId: Long): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE id = :id AND userId = :userId")
    fun getItem(id: Long, userId: Long): Flow<Item?>

    @Query("SELECT * FROM item WHERE id = :id")
    fun getItemById(id: Long): Flow<Item?>

    @Query("SELECT * FROM item WHERE userId = :userId AND name LIKE '%' || :searchTerm || '%'")
    fun searchItems(userId: Long, searchTerm: String): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE userId = :userId AND isFavorite = 1")
    fun getFavoriteItems(userId: Long): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM item WHERE userId = :userId")
    suspend fun getItemCountForUser(userId: Long): Int

    @Query("DELETE FROM item WHERE userId = :userId")
    suspend fun deleteAllItemsForUser(userId: Long)

    // Additional methods for password manager functionality
    @Insert
    suspend fun insertPassword(item: Item): Long

    @Update
    suspend fun updatePassword(item: Item)

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun getPasswordById(id: Long): Item?

    @Delete
    suspend fun deletePassword(item: Item)

    @Query("SELECT * FROM item WHERE userId = :userId")
    fun getPasswordsForUser(userId: Long): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE userId = :userId AND isFavorite = 1")
    fun getFavoritePasswords(userId: Long): Flow<List<Item>>

    @Query("SELECT * FROM item WHERE userId = :userId AND (name LIKE '%' || :query || '%' OR website LIKE '%' || :query || '%')")
    fun searchPasswords(userId: Long, query: String): Flow<List<Item>>

    @Query("DELETE FROM item WHERE userId = :userId")
    suspend fun deleteAllPasswordsForUser(userId: Long)

    @Query("SELECT COUNT(*) FROM item WHERE userId = :userId")
    suspend fun getPasswordCountForUser(userId: Long): Int
}