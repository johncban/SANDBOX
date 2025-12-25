package com.jcb.passbook.data.repository

import android.util.Log
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository pattern for Item operations
 * Fixes: P1 - Wrong method names causing crashes
 */
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao
) {

    private companion object {
        const val TAG = "ItemRepository"
    }

    /**
     * Get all items as a Flow
     */
    fun getAllItems(): Result<List<Item>> = try {
        // Using Result wrapper pattern for error handling
        val items = itemDao.getAllItems()
        Result.success(items)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting all items: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Get item by ID as a Flow
     */
    fun getItemById(id: Int): Flow<Item?> = try {
        itemDao.getItemById(id)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting item by id: ${e.message}", e)
        throw e
    }

    /**
     * Create new item
     * Fixes: P1 - Was using wrong method name
     */
    suspend fun createItem(item: Item): Result<Unit> = try {
        Log.d(TAG, "➕ Creating item: ${item.title}")

        // CRITICAL FIX: Use itemDao.insert() not repository.insert()
        // Fixes: P1 - Wrong method was causing crash on save!
        itemDao.insert(item)

        Log.i(TAG, "✅ Item created successfully: ${item.title}")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error creating item: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Update existing item
     * Fixes: P1 - Was using wrong method name
     */
    suspend fun updateItem(item: Item): Result<Unit> = try {
        Log.d(TAG, "✏️ Updating item: ${item.title}")

        // CRITICAL FIX: Use itemDao.update() not repository.update()
        // Fixes: P1 - Wrong method was causing crash on edit!
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
    suspend fun deleteItem(id: Int): Result<Unit> = try {
        Log.d(TAG, "🗑️ Deleting item with id: $id")

        val rowsAffected = itemDao.delete(id)

        if (rowsAffected > 0) {
            Log.i(TAG, "✅ Item deleted successfully")
            Result.success(Unit)
        } else {
            Log.w(TAG, "⚠️ No rows deleted for id: $id")
            Result.failure(Exception("Item not found"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error deleting item: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Clear all items from vault
     * Fixes: P1 - Missing method (crashes on logout!)
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
     * Fixes: P1 - Missing method (needed for proper user logout)
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
    fun getItemCount(): Result<Int> = try {
        val count = itemDao.getItemCount()
        Log.d(TAG, "📊 Item count: $count")
        Result.success(count)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting item count: ${e.message}", e)
        Result.failure(e)
    }
}
