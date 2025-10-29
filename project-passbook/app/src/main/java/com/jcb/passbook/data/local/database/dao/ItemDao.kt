package com.jcb.passbook.data.local.database.dao

import androidx.room.*
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    // FIXED: Changed "Item" to "items" to match entity tableName
    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY title ASC")
    fun getItemsForUser(userId: Int): Flow<List<Item>>

    // FIXED: Changed "Item" to "items" to match entity tableName
    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId")
    fun getItem(id: Int, userId: Int): Flow<Item?>

    @Query("SELECT * FROM items WHERE userId = :userId AND type = :type ORDER BY title ASC")
    fun getItemsByType(userId: Int, type: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%') ORDER BY title ASC")
    fun searchItems(userId: Int, query: String): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId")
    suspend fun getItemCount(userId: Int): Int

    @Query("DELETE FROM items WHERE userId = :userId")
    suspend fun deleteAllItemsForUser(userId: Int)
}