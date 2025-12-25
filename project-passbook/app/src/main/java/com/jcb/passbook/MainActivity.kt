package com.jcb.passbook

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemDetailScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MainActivity - Single Activity architecture with Jetpack Navigation Compose
 *
 * ✅ FIXED (2025-12-22):
 * - Changed MainActivity to extend FragmentActivity (not ComponentActivity)
 * - Fixed when expression to be exhaustive
 * - Fixed smart cast issues with proper variable extraction
 * - Properly handles SessionManager.startSession() with FragmentActivity context
 *
 * Responsibilities:
 * - Host navigation graph with authentication and vault screens
 * - **CRITICAL**: Observe AuthState and start session + propagate userId to ItemViewModel
 * - Manage application lifecycle (passphrase rotation, session cleanup)
 * - Provide ViewModels via Hilt injection
 * - Coordinate navigation between authentication and vault flows
 * - Handle logout and session termination
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() { // ✅ CHANGED: FragmentActivity instead of ComponentActivity

    @Inject
    lateinit var sessionManager: SessionManager

    // ViewModels scoped to Activity lifecycle via Hilt
    private val userViewModel: UserViewModel by viewModels()
    private val itemViewModel: ItemViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("🏁 MainActivity created, userViewModel=$userViewModel, itemViewModel=$itemViewModel")

        enableEdgeToEdge()
        setupPassphraseRotation()
        setupSessionCleanup()

        // 🔥 CRITICAL: Observe authentication state and set userId
        // This is the PRIMARY fix for "Cannot insert/update item: userId not set"
        setupAuthStateObserver()

        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        userViewModel = userViewModel,
                        itemViewModel = itemViewModel
                    )
                }
            }
        }
    }

    /**
     * 🔥 CRITICAL FIX: Setup AuthState observer
     *
     * This observes the authentication state and propagates userId to ItemViewModel
     * whenever the user successfully logs in or registers.
     *
     * This is the CORE fix for the bug where userId becomes null when saving passwords.
     */
    private fun setupAuthStateObserver() {
        lifecycleScope.launch {
            userViewModel.authState.collect { authState ->
                Timber.d("🔐 AuthState changed: $authState")
                when (authState) {
                    is AuthState.Success -> {
                        val userId = authState.userId
                        Timber.i("🔐 Login/Registration successful! Setting userId=$userId in ItemViewModel")
                        Timber.i("🔐 Current thread: ${Thread.currentThread().name}")

                        // Set userId in ItemViewModel
                        itemViewModel.setCurrentUserId(userId)

                        // Verify it was set
                        Timber.i("🔐 ItemViewModel userId after set: ${itemViewModel.currentUserId.value}")
                    }

                    is AuthState.Idle -> {
                        Timber.d("🔐 Auth state: Idle (user logged out or app started)")
                    }

                    is AuthState.Loading -> {
                        Timber.d("🔐 Auth state: Loading (authentication in progress)")
                    }

                    is AuthState.Error -> {
                        Timber.w("🔐 Auth state: Authentication error - ${authState.messageId}")
                        // Optionally clear userId on error
                        // itemViewModel.clearVault()
                    }
                }
            }
        }
    }

    @Composable
    private fun AppNavigation(
        userViewModel: UserViewModel,
        itemViewModel: ItemViewModel
    ) {
        val navController = rememberNavController()
        val authState by userViewModel.authState.collectAsState()

        // 🔥 ALSO: Re-set userId when authState changes to Success in Compose scope
        // This provides additional safety in case the lifecycleScope observer misses it
        LaunchedEffect(authState) {
            // ✅ FIXED: Extract to local variable for smart cast
            val currentAuthState = authState
            when (currentAuthState) {
                is AuthState.Success -> {
                    // Start session with FragmentActivity context
                    val result = sessionManager.startSession(
                        activity = this@MainActivity,
                        userId = currentAuthState.userId
                    )

                    if (result is SessionManager.SessionResult.Success) {
                        // Navigate to main screen
                        itemViewModel.setCurrentUserId(currentAuthState.userId)
                    }
                }
                is AuthState.Idle,
                is AuthState.Loading,
                is AuthState.Error -> {
                    // ✅ FIXED: Added missing branches to make when exhaustive
                    // No action needed for these states in navigation
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Login.route
        ) {
            // ✅ Authentication flow - LoginScreen
            composable(Screen.Login.route) {
                LoginScreen(
                    userViewModel = userViewModel,
                    onLoginSuccess = { userId: Long ->
                        // Callback invoked after successful authentication AND session start
                        Timber.i("🔐 [LoginScreen Callback] Login success, userId=$userId")

                        // Set userId (redundant with AuthState observer, but provides fallback)
                        itemViewModel.setCurrentUserId(userId)

                        // Navigate to item list
                        navController.navigate(Screen.ItemList.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }

            // ✅ Registration flow
            composable(Screen.Register.route) {
                RegistrationScreen(
                    userViewModel = userViewModel,
                    onRegisterSuccess = { userId: Long ->
                        // Callback invoked after successful registration AND session start
                        Timber.i("🔐 [RegistrationScreen Callback] Registration success, userId=$userId")

                        // Set userId (redundant with AuthState observer, but provides fallback)
                        itemViewModel.setCurrentUserId(userId)

                        // Navigate to item list
                        navController.navigate(Screen.ItemList.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }

            // ✅ Vault flow - ItemListScreen with logout
            composable(Screen.ItemList.route) {
                ItemListScreen(
                    viewModel = itemViewModel,
                    onLogout = {
                        // Handle logout
                        Timber.i("🚪 User logging out")
                        lifecycleScope.launch {
                            try {
                                // Clear user session
                                sessionManager.endSession("User logged out")

                                // Clear vault data
                                itemViewModel.clearVault()

                                // Clear user authentication
                                userViewModel.clearAuthState()

                                Timber.i("🚪 Logout complete, navigating to login")

                                // Navigate back to login
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.ItemList.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error during logout")
                            }
                        }
                    },
                    onAddClick = {
                        // Navigate to add new item screen
                        navController.navigate(Screen.AddItem.route)
                    }
                )
            }

            // ✅ ItemDetailScreen now only for ADDING new items
            // Editing existing items is handled via modal bottom sheet in ItemListScreen
            composable(Screen.AddItem.route) {
                ItemDetailScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    /**
     * Setup passphrase rotation on app lifecycle events
     */
    private fun setupPassphraseRotation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lifecycleScope.launch(Dispatchers.IO) {
                performPassphraseRotationOnOpen()
            }

            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        schedulePassphraseRotationOnClose()
                    }
                }
            })
        }
    }

    /**
     * Setup session cleanup on activity destroy
     */
    private fun setupSessionCleanup() {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                lifecycleScope.launch {
                    Timber.i("🧹 MainActivity destroying, cleaning up session and secrets")
                    sessionManager.endSession("Activity destroyed")
                    itemViewModel.clearSecrets()
                }
            }
        })
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun performPassphraseRotationOnOpen() {
        try {
            if (KeystorePassphraseManager.isRotationNeeded(this)) {
                Timber.i("🔄 Performing passphrase rotation on app open")
                val newPassphrase = KeystorePassphraseManager.generateNewPassphrase()
                val success = KeystorePassphraseManager.commitNewPassphrase(this, newPassphrase)
                if (success) {
                    Timber.i("✅ Passphrase rotated successfully on app open")
                    KeystorePassphraseManager.clearBackup(this)
                } else {
                    Timber.e("❌ Failed to rotate passphrase on app open")
                }
            } else {
                Timber.d("No passphrase rotation needed on app open")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during passphrase rotation on app open")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun schedulePassphraseRotationOnClose() {
        try {
            Timber.i("📅 Scheduling passphrase rotation for next app open")
            KeystorePassphraseManager.markRotationNeeded(this)
        } catch (e: Exception) {
            Timber.e(e, "Error scheduling passphrase rotation")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("🧹 MainActivity destroyed, secrets cleared")
    }

    /**
     * Type-safe navigation routes with compile-time safety
     */
    sealed class Screen(val route: String) {
        object Login : Screen("login")
        object Register : Screen("register")
        object ItemList : Screen("itemList")
        object AddItem : Screen("addItem")
    }
}
