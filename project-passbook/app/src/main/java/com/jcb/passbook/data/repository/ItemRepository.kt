package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.database.dao.ItemDao
import com.jcb.passbook.data.local.database.entities.Item
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ItemRepository @Inject constructor(private val itemDao: ItemDao) {

    // val allItems: Flow<List<Item>> = itemDao.getAllItems()  // Remove or restrict

    fun getItemsForUser(userId: Int): Flow<List<Item>> = itemDao.getItemsForUser(userId)

    fun getItem(id: Int, userId: Int): Flow<Item?> = itemDao.getItem(id, userId)

    suspend fun insert(item: Item) = itemDao.insert(item)

    suspend fun update(item: Item) = itemDao.update(item)

    suspend fun delete(item: Item) = itemDao.delete(item)
}
