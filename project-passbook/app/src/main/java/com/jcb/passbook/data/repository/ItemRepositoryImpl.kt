package com.jcb.passbook.data.repository

import com.jcb.passbook.data.local.dao.ItemDao
import com.jcb.passbook.data.local.entities.Item
import com.jcb.passbook.domain.entities.Password
import com.jcb.passbook.domain.repository.ItemRepository
import com.jcb.passbook.data.mappers.PasswordMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ItemRepositoryImpl @Inject constructor(
    private val itemDao: ItemDao,
    private val passwordMapper: PasswordMapper
) : ItemRepository {

    override suspend fun insertItem(item: Item): Long {
        return itemDao.insert(item)
    }

    override suspend fun updateItem(item: Item) {
        itemDao.update(item)
    }

    override suspend fun deleteItem(item: Item) {
        itemDao.delete(item)
    }

    override fun getItemsForUser(userId: Long): Flow<List<Item>> {
        return itemDao.getItemsForUser(userId)
    }

    override fun getItem(id: Long, userId: Long): Flow<Item?> {
        return itemDao.getItem(id, userId)
    }

    override fun getItemById(id: Long): Flow<Item?> {
        return itemDao.getItemById(id)
    }

    override suspend fun getItemCountForUser(userId: Long): Int {
        return itemDao.getItemCountForUser(userId)
    }

    override suspend fun deleteAllItemsForUser(userId: Long) {
        itemDao.deleteAllItemsForUser(userId)
    }

    // Password-specific methods
    override suspend fun savePassword(password: Password): Long {
        val entity = passwordMapper.toEntity(password)
        return itemDao.insertPassword(entity)
    }

    override suspend fun updatePassword(password: Password) {
        val entity = passwordMapper.toEntity(password)
        itemDao.updatePassword(entity)
    }

    override suspend fun deletePassword(password: Password) {
        val entity = passwordMapper.toEntity(password)
        itemDao.deletePassword(entity)
    }

    override fun getPasswordsForUser(userId: Long): Flow<List<Password>> {
        return itemDao.getPasswordsForUser(userId).map { items ->
            items.map { passwordMapper.toDomain(it) }
        }
    }

    override fun getPasswordById(id: Long): Flow<Password?> {
        return itemDao.getItemById(id).map { item ->
            item?.let { passwordMapper.toDomain(it) }
        }
    }

    override fun getFavoritePasswords(userId: Long): Flow<List<Password>> {
        return itemDao.getFavoritePasswords(userId).map { items ->
            items.map { passwordMapper.toDomain(it) }
        }
    }

    override fun searchPasswords(userId: Long, query: String): Flow<List<Password>> {
        return itemDao.searchPasswords(userId, query).map { items ->
            items.map { passwordMapper.toDomain(it) }
        }
    }

    override suspend fun deleteAllPasswordsForUser(userId: Long) {
        itemDao.deleteAllPasswordsForUser(userId)
    }

    override suspend fun getPasswordCountForUser(userId: Long): Int {
        return itemDao.getPasswordCountForUser(userId)
    }
}