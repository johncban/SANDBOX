package com.jcb.passbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: Item)

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM Item WHERE userId = :userId")
    fun getItemsForUser(userId: Int): Flow<List<Item>>

    @Query("SELECT * FROM Item WHERE id = :id AND userId = :userId")
    fun getItem(id: Int, userId: Int): Flow<Item?> // Ensure only the user's item is fetched

    // Removed or restricted:  Comment out or remove in production
    //@Query("SELECT * FROM Item")
    //fun getAllItems(): Flow<List<Item>> //Potentially remove this, or restrict access
}
