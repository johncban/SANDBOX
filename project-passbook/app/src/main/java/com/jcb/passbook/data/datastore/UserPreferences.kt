package com.jcb.passbook.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * ✅ ENHANCED: UserPreferences with persistent login state
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val CURRENT_USER_ID = intPreferencesKey("current_user_id")
    private val LAST_LOGIN_TIMESTAMP = longPreferencesKey("last_login_timestamp")
    private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")

    /**
     * Current user ID flow
     */
    val currentUserId: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_USER_ID] ?: -1
    }

    /**
     * ✅ NEW: Check if user is logged in
     */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    /**
     * ✅ NEW: Last login timestamp
     */
    val lastLoginTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_LOGIN_TIMESTAMP] ?: 0L
    }

    /**
     * Set current user ID with login timestamp
     */
    suspend fun setCurrentUserId(userId: Int) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_USER_ID] = userId
            preferences[IS_LOGGED_IN] = true
            preferences[LAST_LOGIN_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Clear current user ID (logout)
     */
    suspend fun clearCurrentUserId() {
        context.dataStore.edit { preferences ->
            preferences.remove(CURRENT_USER_ID)
            preferences[IS_LOGGED_IN] = false
        }
    }

    /**
     * ✅ NEW: Get current user ID synchronously (for init checks)
     */
    suspend fun getCurrentUserIdSync(): Int {
        var userId = -1
        context.dataStore.edit { preferences ->
            userId = preferences[CURRENT_USER_ID] ?: -1
        }
        return userId
    }
}
