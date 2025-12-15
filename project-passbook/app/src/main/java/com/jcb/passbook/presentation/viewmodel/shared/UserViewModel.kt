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
import java.security.MessageDigest
import java.security.SecureRandom
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
                        Log.d(TAG, "✅ User loaded: ${currentUser.username}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user", e)
            }
        }
    }

    /**
     * ✅ Generate a random salt for password hashing
     */
    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    /**
     * ✅ SHA-256 password hashing WITH salt parameter
     */
    private fun hashPassword(password: String, salt: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val saltedPassword = password + salt
            val hashBytes = digest.digest(saltedPassword.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing password", e)
            password // Fallback (NOT recommended in production)
        }
    }

    /**
     * ✅ Register a new user
     */
    fun register(username: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Log.d(TAG, "Starting registration for user: $username")

                // Check if user already exists
                val existingUser = userRepository.getUserByUsername(username)
                if (existingUser != null) {
                    _authState.value = AuthState.Error("Username already exists")
                    Log.w(TAG, "Registration failed: Username already exists")
                    return@launch
                }

                // Generate salt for this user
                val salt = generateSalt()

                // Hash the password with salt
                val hashedPassword = hashPassword(password, salt)

                // Create new user entity WITH SALT
                val newUser = User(
                    id = 0,
                    username = username,
                    passwordHash = hashedPassword,
                    salt = salt,  // ✅ ADDED SALT PARAMETER
                    createdAt = System.currentTimeMillis()
                )

                // Insert user into database
                val userId = userRepository.insertUser(newUser)
                Log.d(TAG, "✅ User inserted with ID: $userId")

                // Create the complete user object with the actual ID
                val insertedUser = newUser.copy(id = userId)

                // Set as current user in preferences
                userRepository.setCurrentUserId(userId.toInt())
                Log.d(TAG, "✅ Current user ID set to: $userId")

                // Update local state with the complete user
                _user.value = insertedUser
                _authState.value = AuthState.Success(userId)

                Log.d(TAG, "✅ Registration successful for: $username")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Registration error", e)
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    /**
     * ✅ Login with password verification
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Log.d(TAG, "Attempting login for user: $username")

                // First, get the user to retrieve their salt
                val user = userRepository.getUserByUsername(username)

                if (user == null) {
                    _authState.value = AuthState.Error("Invalid credentials")
                    Log.w(TAG, "❌ Login failed: User not found")
                    return@launch
                }

                // Hash the input password with the user's salt
                val hashedPassword = hashPassword(password, user.salt)

                // Authenticate user with hashed password
                val authenticatedUser = userRepository.authenticateUser(username, hashedPassword)

                if (authenticatedUser != null) {
                    // Set as current user in preferences
                    userRepository.setCurrentUserId(authenticatedUser.id.toInt())
                    Log.d(TAG, "✅ Current user ID set to: ${authenticatedUser.id}")

                    // Update local state
                    _user.value = authenticatedUser
                    _authState.value = AuthState.Success(authenticatedUser.id)

                    Log.d(TAG, "✅ Login successful: ${authenticatedUser.username}")
                } else {
                    _authState.value = AuthState.Error("Invalid credentials")
                    Log.w(TAG, "❌ Login failed: Invalid credentials")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Login error", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    /**
     * ✅ Logout user - THIS SHOULD ONLY BE CALLED BY USER ACTION, NOT ON INIT!
     */
    fun logout() {
        viewModelScope.launch {
            try {
                userRepository.clearCurrentUser()
                _user.value = null
                _authState.value = AuthState.Idle
                Log.d(TAG, "✅ User logged out")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Logout error", e)
            }
        }
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }
}
