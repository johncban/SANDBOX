package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ItemRepository"

/**
 * ✅ CRITICAL FIX: All operations now enforce userId filtering
 * - Removed global `allItems` accessor (security risk)
 * - All queries require explicit userId parameter
 * - Ownership validation on all mutations
 * - Prevents cross-user data access
 * - Fixed all type mismatches (Long consistency)
 */
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao
) {

    /**
     * ✅ Get items for specific user only
     * @param userId User ID whose items to fetch
     * @return Flow of items belonging to user, ordered by creation date
     */
    fun getItemsForUser(userId: Long): Flow<List<Item>> {
        require(userId > 0) { "Invalid userId: $userId" }
        Timber.tag(TAG).d("Fetching items for user: $userId")
        return itemDao.getItemsForUser(userId)
    }

    /**
     * ✅ Get single item with userId validation
     * @param id Item ID
     * @param userId User ID (for ownership validation)
     * @return Flow of item if found and belongs to user, null otherwise
     */
    fun getItem(id: Long, userId: Long): Flow<Item?> {
        require(userId > 0) { "Invalid userId: $userId" }
        require(id > 0) { "Invalid item id: $id" }
        Timber.tag(TAG).d("Fetching item $id for user $userId")
        return itemDao.getItem(id, userId)
    }

    /**
     * ✅ CRITICAL: Validate userId before insert
     * @param item Item to insert (must have valid userId)
     * @throws IllegalArgumentException if userId is invalid
     */
    suspend fun insert(item: Item) {
        require(item.userId > 0) { "Cannot insert item without valid userId" }
        Timber.tag(TAG).d("Inserting item for user: ${item.userId}")
        itemDao.insert(item)
    }

    /**
     * ✅ CRITICAL: Validate userId and ownership before update
     * Security: Prevents user from updating another user's item
     * @param item Item to update
     * @throws IllegalArgumentException if item doesn't exist
     * @throws SecurityException if user doesn't own the item
     */
    suspend fun update(item: Item) {
        require(item.userId > 0) { "Cannot update item without valid userId" }
        require(item.id > 0) { "Cannot update item without valid id" }

        // ✅ Verify item exists and belongs to user
        val existing = itemDao.getItemById(item.id)
        if (existing == null) {
            Timber.tag(TAG).e("Cannot update non-existent item: ${item.id}")
            throw IllegalArgumentException("Item ${item.id} does not exist")
        }

        if (existing.userId != item.userId) {
            Timber.tag(TAG).e(
                "Security: User ${item.userId} attempted to update item ${item.id} owned by user ${existing.userId}"
            )
            throw SecurityException("Cannot update item owned by another user")
        }

        Timber.tag(TAG).d("Updating item ${item.id} for user ${item.userId}")
        itemDao.update(item)
    }

    /**
     * ✅ CRITICAL: Validate ownership before delete
     * Security: Prevents user from deleting another user's item
     * @param item Item to delete
     * @throws SecurityException if user doesn't own the item
     */
    suspend fun delete(item: Item) {
        require(item.userId > 0) { "Cannot delete item without valid userId" }
        require(item.id > 0) { "Cannot delete item without valid id" }

        // ✅ Verify ownership
        val existing = itemDao.getItemById(item.id)
        if (existing == null) {
            Timber.tag(TAG).w("Attempted to delete non-existent item: ${item.id}")
            return
        }

        if (existing.userId != item.userId) {
            Timber.tag(TAG).e(
                "Security: User ${item.userId} attempted to delete item ${item.id} owned by user ${existing.userId}"
            )
            throw SecurityException("Cannot delete item owned by another user")
        }

        Timber.tag(TAG).d("Deleting item ${item.id} for user ${item.userId}")
        itemDao.delete(item)
    }

    /**
     * ✅ Get count of items for user
     * Useful for showing item count in UI
     */
    suspend fun getItemCountForUser(userId: Long): Int {
        require(userId > 0) { "Invalid userId: $userId" }
        return itemDao.getItemCountForUser(userId)
    }

    /**
     * ✅ Search user's items
     * Security: Search is scoped to user's items only
     */
    fun searchItems(userId: Long, query: String): Flow<List<Item>> {
        require(userId > 0) { "Invalid userId: $userId" }
        require(query.isNotBlank()) { "Search query cannot be empty" }
        Timber.tag(TAG).d("Searching items for user $userId with query: $query")
        return itemDao.searchItems(userId, query)
    }

    /**
     * ✅ Get items by type for user
     * @param userId User ID
     * @param itemType Item type (e.g., "password", "note", "credit_card")
     */
    fun getItemsByType(userId: Long, itemType: String): Flow<List<Item>> {
        require(userId > 0) { "Invalid userId: $userId" }
        require(itemType.isNotBlank()) { "Item type cannot be empty" }
        Timber.tag(TAG).d("Fetching $itemType items for user $userId")
        return itemDao.getItemsByType(userId, itemType)
    }

    /**
     * ✅ Delete all items for user (use with caution - e.g., account deletion)
     * Security: Only deletes items belonging to the specified user
     */
    suspend fun deleteAllForUser(userId: Long) {
        require(userId > 0) { "Invalid userId: $userId" }
        Timber.tag(TAG).w("Deleting all items for user: $userId")
        itemDao.deleteAllForUser(userId)
    }
}
