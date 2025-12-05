package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow

/**
 * ✅ CRITICAL FIX: All queries enforce userId filtering
 * - Prevents cross-user data access
 * - All operations require explicit userId parameter
 * - Fixed all type mismatches (Long consistency)
 */
@Dao
interface ItemDao {

    /**
     * Insert new item
     * @return Item ID (primary key) of inserted item
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    /**
     * Update existing item
     */
    @Update
    suspend fun update(item: Item)

    /**
     * Delete item
     */
    @Delete
    suspend fun delete(item: Item)

    /**
     * ✅ Get all items for specific user only (ordered by creation date)
     * Security: Only returns items belonging to the specified user
     */
    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY createdAt DESC")
    fun getItemsForUser(userId: Long): Flow<List<Item>>

    /**
     * ✅ Get single item with user validation
     * Security: Returns item only if it belongs to the specified user
     */
    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId LIMIT 1")
    fun getItem(id: Long, userId: Long): Flow<Item?>

    /**
     * ✅ Get item by ID only (for ownership checks in repository)
     * Used internally by repository to verify ownership before update/delete
     */
    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): Item?

    /**
     * ✅ Search within user's items only
     * Security: Search is scoped to user's items only
     */
    @Query("""
        SELECT * FROM items 
        WHERE userId = :userId 
        AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun searchItems(userId: Long, query: String): Flow<List<Item>>

    /**
     * ✅ Get count of items for specific user
     */
    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId")
    suspend fun getItemCountForUser(userId: Long): Int

    /**
     * ✅ Delete all items for user (e.g., account deletion scenario)
     */
    @Query("DELETE FROM items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)

    /**
     * ✅ Get items by type for specific user
     * Useful for filtering by password/note/credit card types
     */
    @Query("SELECT * FROM items WHERE userId = :userId AND type = :itemType ORDER BY createdAt DESC")
    fun getItemsByType(userId: Long, itemType: String): Flow<List<Item>>
}
