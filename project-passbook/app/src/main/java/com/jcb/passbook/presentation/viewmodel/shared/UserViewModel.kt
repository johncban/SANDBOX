package com.jcb.passbook.presentation.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.entities.User
import com.jcb.passbook.data.repositories.UserRepository  // Changed to repositories (plural)
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val TAG = "UserViewModel"
    }

    // Add explicit type parameters
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        Log.d(TAG, "UserViewModel initialized")
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                userRepository.getCurrentUser().collect { currentUser ->
                    _user.value = currentUser
                    if (currentUser == null) {
                        Log.d(TAG, "No user found in DataStore or database")
                    } else {
                        Log.d(TAG, "User loaded: ${currentUser.username}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val authenticatedUser = userRepository.authenticateUser(username, password)
                if (authenticatedUser != null) {
                    _user.value = authenticatedUser
                    Log.d(TAG, "User logged in successfully: ${authenticatedUser.username}")
                } else {
                    _error.value = "Invalid credentials"
                    Log.w(TAG, "Login failed: Invalid credentials")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _error.value = e.message ?: "Login failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                userRepository.clearCurrentUser()
                _user.value = null
                Log.d(TAG, "User logged out successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
