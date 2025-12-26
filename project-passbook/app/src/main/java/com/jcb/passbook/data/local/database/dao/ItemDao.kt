package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Item entity
 * All database operations for password items
 */
@Dao
interface ItemDao {

    // ========================================
    // CRUD OPERATIONS
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item): Int

    @Delete
    suspend fun delete(item: Item)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    @Query("DELETE FROM items WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)

    // ========================================
    // QUERY OPERATIONS
    // ========================================

    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getItemsForUser(userId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId")
    fun getItem(id: Long, userId: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemById(id: Long): Flow<Item?>

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int

    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId")
    suspend fun getItemCountForUser(userId: Long): Int

    // ========================================
    // CATEGORY-BASED QUERIES
    // ========================================

    @Query("SELECT * FROM items WHERE userId = :userId AND password_category = :category ORDER BY updatedAt DESC")
    fun getItemsByCategory(userId: Long, category: String): Flow<List<Item>>

    @Query("""
        SELECT * FROM items 
        WHERE userId = :userId 
        AND (:category IS NULL OR password_category = :category)
        AND (title LIKE '%' || :searchQuery || '%' 
             OR username LIKE '%' || :searchQuery || '%' 
             OR notes LIKE '%' || :searchQuery || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchItems(userId: Long, searchQuery: String, category: String?): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId AND password_category = :category")
    fun getCountByCategory(userId: Long, category: String): Flow<Int>

    @Query("SELECT * FROM items WHERE userId = :userId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteItems(userId: Long): Flow<List<Item>>
}
