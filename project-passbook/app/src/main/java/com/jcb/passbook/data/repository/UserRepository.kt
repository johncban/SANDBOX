package com.jcb.passbook.data.repository

import com.jcb.passbook.data.datastore.UserPreferences
import com.jcb.passbook.data.local.database.dao.UserDao
import com.jcb.passbook.data.local.database.entities.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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
     * ✅ FIXED: Now properly extracts User from Flow<List<User>>
     * Returns: User? (single user or null)
     */
    suspend fun getUserByUsername(username: String): User? {
        return try {
            // getAllUsers() returns Flow<List<User>>
            // firstOrNull() gets the first emission
            // find() searches through the list
            userDao.getAllUsers()
                .firstOrNull() // Get the emitted List<User>
                ?.find { it.username == username } // Search for matching username
                ?: run {
                    Timber.tag(TAG).d("No user found with username: $username")
                    null
                }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting user by username: $username")
            null
        }
    }

    /**
     * Get current user ID from DataStore
     * Returns: Flow<Int> - emits user ID continuously
     */
    fun getCurrentUserId(): Flow<Int> {
        return userPreferences.currentUserId
    }

    /**
     * Set current user ID in DataStore
     * ✅ Persists user ID across app restarts
     */
    suspend fun setCurrentUserId(userId: Int) {
        try {
            userPreferences.setCurrentUserId(userId)
            Timber.tag(TAG).d("✓ Set current user ID: $userId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error setting current user ID: ${e.message}")
        }
    }

    /**
     * Clear current user ID from DataStore
     * ✅ Called on logout
     */
    suspend fun clearCurrentUserId() {
        try {
            userPreferences.clearCurrentUserId()
            Timber.tag(TAG).d("✓ Cleared current user ID")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error clearing current user ID: ${e.message}")
        }
    }

    /**
     * Insert new user into database
     * Returns: Long - the inserted user's ID
     */
    suspend fun insertUser(user: User): Long {
        return try {
            val userId = userDao.insert(user)
            Timber.tag(TAG).d("✓ Inserted user: $userId")
            userId
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error inserting user: ${e.message}")
            -1L // Return -1 on failure
        }
    }

    /**
     * Update existing user
     */
    suspend fun updateUser(user: User) {
        try {
            userDao.update(user)
            Timber.tag(TAG).d("✓ Updated user: ${user.id}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error updating user: ${e.message}")
        }
    }

    /**
     * Delete user
     */
    suspend fun deleteUser(user: User) {
        try {
            userDao.delete(user)
            Timber.tag(TAG).d("✓ Deleted user: ${user.id}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting user: ${e.message}")
        }
    }

    /**
     * Get all users as Flow
     * Returns: Flow<List<User>> - emits updated list on changes
     */
    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    /**
     * Get active user (first user in database as fallback)
     * ✅ FIXED: Properly maps Flow<List<User>> to Flow<User?>
     * Returns: Flow<User?> - emits first user or null
     */
    fun getActiveUser(): Flow<User?> {
        return userDao.getAllUsers().map { users ->
            users.firstOrNull()
        }
    }

    /**
     * Get user by ID (suspend function)
     * Returns: User? (single user or null)
     */
    suspend fun getUserById(userId: Long): User? {
        return try {
            userDao.getUserById(userId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting user by ID: $userId")
            null
        }
    }

    /**
     * Check if username exists (suspend function)
     * Returns: Boolean - true if user exists
     */
    suspend fun userExists(username: String): Boolean {
        return try {
            userDao.userExists(username)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking if user exists: $username")
            false
        }
    }

    /**
     * Get user count (suspend function)
     * Returns: Int - number of users in database
     */
    suspend fun getUserCount(): Int {
        return try {
            userDao.getUserCount()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error getting user count")
            0
        }
    }
}
