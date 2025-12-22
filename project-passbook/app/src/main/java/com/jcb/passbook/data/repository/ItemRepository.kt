package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import com.jcb.passbook.data.local.database.entities.PasswordCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao
) {
    fun getItemsForUser(userId: Long): Flow<List<Item>> {
        return itemDao.getItemsForUser(userId)
    }

    fun getItem(id: Long, userId: Long): Flow<Item?> {
        return itemDao.getItem(id, userId)
    }

    suspend fun insertItem(item: Item): Long {
        return itemDao.insert(item)
    }

    suspend fun updateItem(item: Item) {
        itemDao.update(item)
    }

    suspend fun deleteItem(item: Item) {
        itemDao.delete(item)
    }

    /**
     * ✅ NEW: Delete item by ID
     * Required for ItemViewModel.deleteItem(itemId: Long)
     */
    suspend fun deleteById(itemId: Long) {
        itemDao.deleteById(itemId)
    }

    // NEW: Category-based operations
    fun getItemsByCategory(userId: Long, category: PasswordCategory): Flow<List<Item>> {
        return itemDao.getItemsByCategory(userId, category.name)
    }

    fun searchItems(
        userId: Long,
        searchQuery: String,
        category: PasswordCategory? = null
    ): Flow<List<Item>> {
        return itemDao.searchItems(userId, searchQuery, category?.name)
    }

    fun getCountByCategory(userId: Long, category: PasswordCategory): Flow<Int> {
        return itemDao.getCountByCategory(userId, category.name)
    }

    fun getFavoriteItems(userId: Long): Flow<List<Item>> {
        return itemDao.getFavoriteItems(userId)
    }
}
