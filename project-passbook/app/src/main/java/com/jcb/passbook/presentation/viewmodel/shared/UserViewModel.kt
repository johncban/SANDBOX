package com.jcb.passbook.presentation.viewmodel.shared

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcb.passbook.data.local.database.entities.User
import com.jcb.passbook.data.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userId: Long) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val TAG = "UserViewModel"
    }

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        Log.d(TAG, "UserViewModel initialized")
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            try {
                userRepository.getCurrentUser().collect { currentUser ->
                    _user.value = currentUser
                    if (currentUser == null) {
                        Log.d(TAG, "No user found")
                    } else {
                        Log.d(TAG, "User loaded: ${currentUser.username}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user", e)
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading

                val authenticatedUser = userRepository.authenticateUser(username, password)
                if (authenticatedUser != null) {
                    _user.value = authenticatedUser
                    _authState.value = AuthState.Success(authenticatedUser.id)
                    Log.d(TAG, "Login successful: ${authenticatedUser.username}")
                } else {
                    _authState.value = AuthState.Error("Invalid credentials")
                    Log.w(TAG, "Login failed: Invalid credentials")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                userRepository.clearCurrentUser()
                _user.value = null
                _authState.value = AuthState.Idle
                Log.d(TAG, "User logged out")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
            }
        }
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }
}
