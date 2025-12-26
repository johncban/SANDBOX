package com.jcb.passbook.data.repository

import android.util.Log
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Repository for Item operations
 * Provides clean API for data access with proper error handling
 */
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao
) {

    private companion object {
        const val TAG = "ItemRepository"
    }

    /**
     * Get all items - returns Flow collected to List
     */
    suspend fun getAllItems(): Result<List<Item>> = try {
        val items = itemDao.getAllItems().first()
        Log.d(TAG, "📦 Retrieved ${items.size} items")
        Result.success(items)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting all items: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Get item by ID as Flow
     */
    fun getItemById(id: Long): Flow<Item?> = try {
        itemDao.getItemById(id)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting item by id: ${e.message}", e)
        throw e
    }

    /**
     * Create new item
     */
    suspend fun createItem(item: Item): Result<Unit> = try {
        Log.d(TAG, "➕ Creating item: ${item.title}")
        itemDao.insert(item)
        Log.i(TAG, "✅ Item created successfully: ${item.title}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error creating item: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Update existing item
     */
    suspend fun updateItem(item: Item): Result<Unit> = try {
        Log.d(TAG, "✏️ Updating item: ${item.title}")
        val rowsAffected = itemDao.update(item)

        if (rowsAffected > 0) {
            Log.i(TAG, "✅ Item updated successfully: ${item.title}")
            Result.success(Unit)
        } else {
            Log.w(TAG, "⚠️ No rows updated for item: ${item.title}")
            Result.failure(Exception("Item not found"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error updating item: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Delete item by ID
     */
    suspend fun deleteItem(id: Long): Result<Unit> = try {
        Log.d(TAG, "🗑️ Deleting item with id: $id")
        val item = itemDao.getItemById(id).first()

        item?.let {
            itemDao.delete(it)
            Log.i(TAG, "✅ Item deleted successfully")
            Result.success(Unit)
        } ?: run {
            Log.w(TAG, "⚠️ Item not found for id: $id")
            Result.failure(Exception("Item not found"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error deleting item: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Clear all items from vault
     */
    suspend fun clearVault(): Result<Unit> = try {
        Log.d(TAG, "🧹 Clearing entire vault...")
        itemDao.deleteAll()
        Log.i(TAG, "✅ Vault cleared successfully")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error clearing vault: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Clear items for specific user
     */
    suspend fun clearVaultForUser(userId: String): Result<Unit> = try {
        Log.d(TAG, "🧹 Clearing vault for user: $userId")
        itemDao.deleteByUserId(userId)
        Log.i(TAG, "✅ Vault cleared for user: $userId")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error clearing vault for user: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Get count of items
     */
    suspend fun getItemCount(): Result<Int> = try {
        val count = itemDao.getItemCount()
        Log.d(TAG, "📊 Item count: $count")
        Result.success(count)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting item count: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Search items with optional category filter
     */
    fun searchItems(userId: Long, query: String, category: String?): Flow<List<Item>> {
        return itemDao.searchItems(userId, query, category)
    }

    /**
     * Get items by category
     */
    fun getItemsByCategory(userId: Long, category: String): Flow<List<Item>> {
        return itemDao.getItemsByCategory(userId, category)
    }

    /**
     * Get favorite items
     */
    fun getFavoriteItems(userId: Long): Flow<List<Item>> {
        return itemDao.getFavoriteItems(userId)
    }
}
