package com.jcb.passbook.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.data.local.database.dao.CategoryDao
import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing password items
 *
 * ✅ FIXED: Removed PasswordCategoryDao dependency since it's not in AppDatabase
 *
 * IMPORTANT: This version manages items WITHOUT the password_category junction table.
 * If you need category associations in the future:
 * 1. Add PasswordCategoryDao to AppDatabase
 * 2. Add passwordCategoryDao parameter back to constructor
 * 3. Uncomment category management methods
 */
@Singleton
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    // ❌ REMOVED: private val passwordCategoryDao: PasswordCategoryDao,
    private val database: AppDatabase
) {

    companion object {
        private const val TAG = "ItemRepository"
    }

    // ========================================
    // 🔹 CRUD OPERATIONS WITH TRANSACTIONS
    // ========================================

    /**
     * Insert a new password item
     *
     * ✅ SIMPLIFIED: Category associations removed until PasswordCategoryDao is added to AppDatabase
     */
    suspend fun insertItem(item: Item): Result<Long> {
        return try {
            val itemId = database.withTransaction {
                itemDao.insert(item)
            }

            Log.d(TAG, "Inserted item with ID: $itemId for user: ${item.userId}")
            Result.success(itemId)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting item for user ${item.userId}", e)
            Result.failure(
                ItemRepositoryException(
                    "Failed to insert password item: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Update an existing password item
     *
     * ✅ SIMPLIFIED: Category associations removed
     */
    suspend fun updateItem(item: Item): Result<Unit> {
        return try {
            database.withTransaction {
                itemDao.update(item)
            }

            Log.d(TAG, "Updated item ${item.id} for user ${item.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating item ${item.id}", e)
            Result.failure(
                ItemRepositoryException(
                    "Failed to update password item: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Delete an item
     */
    suspend fun deleteItem(item: Item): Result<Unit> {
        return try {
            database.withTransaction {
                itemDao.delete(item)
            }

            Log.d(TAG, "Deleted item ${item.id} for user ${item.userId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item ${item.id}", e)
            Result.failure(
                ItemRepositoryException(
                    "Failed to delete password item: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Delete item by ID
     */
    suspend fun deleteById(itemId: Long): Result<Unit> {
        return try {
            database.withTransaction {
                itemDao.deleteById(itemId)
            }

            Log.d(TAG, "Deleted item by ID: $itemId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting item by ID $itemId", e)
            Result.failure(
                ItemRepositoryException(
                    "Failed to delete password item by ID: ${e.message}",
                    e
                )
            )
        }
    }

    // ========================================
    // 🔹 QUERY OPERATIONS (READ-ONLY)
    // ========================================

    fun getItemsForUser(userId: Long): Flow<List<Item>> {
        return itemDao.getItemsForUser(userId)
            .catch { e ->
                Log.e(TAG, "Error fetching items for user $userId", e)
                emit(emptyList())
            }
    }

    fun getItem(id: Long, userId: Long): Flow<Item?> {
        return itemDao.getItem(id, userId)
            .catch { e ->
                Log.e(TAG, "Error fetching item $id for user $userId", e)
                emit(null)
            }
    }

    fun getItemById(id: Long): Flow<Item?> {
        return itemDao.getItemById(id)
            .catch { e ->
                Log.e(TAG, "Error fetching item by ID $id", e)
                emit(null)
            }
    }

    fun getAllItems(): Flow<List<Item>> {
        return itemDao.getAllItems()
            .catch { e ->
                Log.e(TAG, "Error fetching all items", e)
                emit(emptyList())
            }
    }

    // ========================================
    // 🔹 CATEGORY-BASED OPERATIONS
    // ========================================

    fun getItemsByCategory(userId: Long, category: String): Flow<List<Item>> {
        return itemDao.getItemsByCategory(userId, category)
            .catch { e ->
                Log.e(TAG, "Error fetching items by category $category", e)
                emit(emptyList())
            }
    }

    fun searchItems(
        userId: Long,
        searchQuery: String,
        category: String? = null
    ): Flow<List<Item>> {
        return itemDao.searchItems(userId, searchQuery, category)
            .catch { e ->
                Log.e(TAG, "Error searching items with query: $searchQuery", e)
                emit(emptyList())
            }
    }

    fun getCountByCategory(userId: Long, category: String): Flow<Int> {
        return itemDao.getCountByCategory(userId, category)
            .catch { e ->
                Log.e(TAG, "Error getting count for category $category", e)
                emit(0)
            }
    }

    fun getFavoriteItems(userId: Long): Flow<List<Item>> {
        return itemDao.getFavoriteItems(userId)
            .catch { e ->
                Log.e(TAG, "Error fetching favorite items for user $userId", e)
                emit(emptyList())
            }
    }

    // ========================================
    // 🔹 STATISTICS & ANALYTICS
    // ========================================

    fun getTotalItemCount(userId: Long): Flow<Int> {
        return getItemsForUser(userId).map { it.size }
    }

    suspend fun itemExists(itemId: Long, userId: Long): Boolean {
        return try {
            var exists = false
            getItem(itemId, userId).collect { item ->
                exists = item != null
            }
            exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if item exists: $itemId", e)
            false
        }
    }
}

/**
 * Custom exception for repository operations
 */
class ItemRepositoryException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
