package com.jcb.passbook.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "security_settings")

@Singleton
class SecurityPreferences @Inject constructor(
    private val context: Context
) {

    companion object {
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val AUTO_LOCK_TIMEOUT = longPreferencesKey("auto_lock_timeout")
        private val FAILED_ATTEMPTS_LIMIT = intPreferencesKey("failed_attempts_limit")
        private val CURRENT_USER_ID = longPreferencesKey("current_user_id")
        private val SESSION_TOKEN = stringPreferencesKey("session_token")
    }

    val biometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[BIOMETRIC_ENABLED] ?: false }

    val autoLockTimeout: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[AUTO_LOCK_TIMEOUT] ?: 300000L } // 5 minutes default

    val failedAttemptsLimit: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[FAILED_ATTEMPTS_LIMIT] ?: 5 }

    val currentUserId: Flow<Long?> = context.dataStore.data
        .map { preferences -> preferences[CURRENT_USER_ID] }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setAutoLockTimeout(timeout: Long) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_LOCK_TIMEOUT] = timeout
        }
    }

    suspend fun setCurrentUserId(userId: Long?) {
        context.dataStore.edit { preferences ->
            if (userId != null) {
                preferences[CURRENT_USER_ID] = userId
            } else {
                preferences.remove(CURRENT_USER_ID)
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(CURRENT_USER_ID)
            preferences.remove(SESSION_TOKEN)
        }
    }
}
