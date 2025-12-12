package com.jcb.passbook.data.repository

import com.jcb.passbook.data.datastore.UserPreferences
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val userPreferences: UserPreferences
) {

    companion object {
        private const val TAG = "UserRepository"
    }

    /**
     * Get user by username (suspend function)
     */
    suspend fun getUserByUsername(username: String): User? {
        return try {
            userDao.getUserByUsernameFlow(username).firstOrNull()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting user by username: $username")
            null
        }
    }

    /**
     * Get current user ID from DataStore
     */
    fun getCurrentUserId(): Flow<Int> = userPreferences.currentUserId

    /**
     * Set current user ID in DataStore
     */
    suspend fun setCurrentUserId(userId: Int) {
        try {
            userPreferences.setCurrentUserId(userId)
            Timber.tag(TAG).d("Set current user ID: $userId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error setting current user ID")
        }
    }

    /**
     * Clear current user ID from DataStore
     */
    suspend fun clearCurrentUserId() {
        try {
            userPreferences.clearCurrentUserId()
            Timber.tag(TAG).d("Cleared current user ID")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error clearing current user ID")
        }
    }

    /**
     * Insert new user
     */
    suspend fun insertUser(user: User): Long {
        return try {
            val userId = userDao.insert(user)
            Timber.tag(TAG).d("Inserted user: $userId")
            userId
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error inserting user")
            -1L
        }
    }

    /**
     * Update existing user
     */
    suspend fun updateUser(user: User) {
        try {
            userDao.update(user)
            Timber.tag(TAG).d("Updated user: ${user.id}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating user")
        }
    }

    /**
     * Delete user
     */
    suspend fun deleteUser(user: User) {
        try {
            userDao.delete(user)
            Timber.tag(TAG).d("Deleted user: ${user.id}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting user")
        }
    }

    /**
     * Get all users as Flow
     */
    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    /**
     * Get active user
     */
    fun getActiveUser(): Flow<User?> {
        return userDao.getActiveUserFlow()
    }
}
