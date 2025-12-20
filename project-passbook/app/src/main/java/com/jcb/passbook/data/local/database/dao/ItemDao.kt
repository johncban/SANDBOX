package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getItemsForUser(userId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId")
    fun getItem(id: Long, userId: Long): Flow<Item?>

    // NEW: Filter by password category enum
    @Query("SELECT * FROM items WHERE userId = :userId AND password_category = :category ORDER BY updatedAt DESC")
    fun getItemsByCategory(userId: Long, category: String): Flow<List<Item>>

    // NEW: Search items with category filter
    @Query("""
        SELECT * FROM items 
        WHERE userId = :userId 
        AND (:category IS NULL OR password_category = :category)
        AND (title LIKE '%' || :searchQuery || '%' OR username LIKE '%' || :searchQuery || '%' OR notes LIKE '%' || :searchQuery || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchItems(userId: Long, searchQuery: String, category: String?): Flow<List<Item>>

    // NEW: Get count by category for stats
    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId AND password_category = :category")
    fun getCountByCategory(userId: Long, category: String): Flow<Int>

    @Query("SELECT * FROM items WHERE userId = :userId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteItems(userId: Long): Flow<List<Item>>
}
