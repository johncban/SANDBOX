package com.jcb.passbook.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Item entity
 *
 * ✅ FIXED ISSUES:
 * 1. Removed duplicate method declarations
 * 2. Standardized column names to "userId" (matches Item entity @ColumnInfo)
 * 3. All queries use consistent column naming
 */
@Dao
interface ItemDao {

    // ========================================
    // CRUD OPERATIONS
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long)

    // ========================================
    // QUERY OPERATIONS - Fixed column names
    // ========================================

    /**
     * Get all items for a user
     * ✅ FIXED: Changed user_id → userId
     */
    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getItemsForUser(userId: Long): Flow<List<Item>>

    /**
     * Get single item by ID and user (security check)
     * ✅ FIXED: Changed user_id → userId
     */
    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId")
    fun getItem(id: Long, userId: Long): Flow<Item?>

    /**
     * Get item by ID only (use with caution - no user check)
     */
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemById(id: Long): Flow<Item?>

    /**
     * Get all items (admin/debug use only)
     */
    @Query("SELECT * FROM items")
    fun getAllItems(): Flow<List<Item>>

    // ========================================
    // CATEGORY-BASED QUERIES
    // ========================================

    /**
     * Filter by password category enum
     * ✅ FIXED: Changed user_id → userId
     */
    @Query("SELECT * FROM items WHERE userId = :userId AND password_category = :category ORDER BY updatedAt DESC")
    fun getItemsByCategory(userId: Long, category: String): Flow<List<Item>>

    /**
     * Search items with optional category filter
     * ✅ FIXED: Changed user_id → userId
     */
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

    /**
     * Get count by category for statistics
     * ✅ FIXED: Changed user_id → userId
     */
    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId AND password_category = :category")
    fun getCountByCategory(userId: Long, category: String): Flow<Int>

    /**
     * Get favorite items
     * ✅ FIXED: Changed user_id → userId
     */
    @Query("SELECT * FROM items WHERE userId = :userId AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteItems(userId: Long): Flow<List<Item>>
}
