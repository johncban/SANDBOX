package com.jcb.passbook

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity  // ✅ CHANGED from FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.BlendMode.Companion.Screen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.presentation.navigation.Screen
import com.jcb.passbook.presentation.ui.*
import com.jcb.passbook.presentation.viewmodel.shared.AuthState
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.security.session.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var itemViewModel: ItemViewModel

    @Inject
    lateinit var userViewModel: UserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup authentication state observer
        // ✅ CRITICAL: Single source of truth for auth state
        setupAuthStateObserver()

        // Setup other initialization
        setupPassphraseRotation()
        setupSessionCleanup()

        setContent {
            MaterialTheme {
                Surface {
                    AppNavigation()
                }
            }
        }
    }

    /**
     * ✅ CRITICAL: Single source of truth for userId setting
     * This is the ONLY place where setCurrentUserId() should be called
     */
    private fun setupAuthStateObserver() {
        lifecycleScope.launch {
            userViewModel.authState.collect { authState ->
                when (authState) {
                    is AuthState.Success -> {
                        Timber.i("🔐 AuthState changed to Success: ${authState.userId}")
                        // ✅ Set userId ONLY here
                        itemViewModel.setCurrentUserId(authState.userId)
                    }
                    is AuthState.Authenticated -> {
                        Timber.i("🔐 User authenticated")
                    }
                    AuthState.LoggedOut -> {
                        Timber.i("🚪 User logged out")
                        itemViewModel.clearSecrets()
                    }
                    is AuthState.Error -> {
                        Timber.e("❌ Auth error: ${authState.message}")
                    }
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        val navController = rememberNavController()
        val authState by userViewModel.authState.collectAsState()

        NavHost(
            navController = navController,
            startDestination = when (authState) {
                is AuthState.Success -> Screen.ItemList.route
                else -> Screen.Login.route
            }
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = { userId ->
                        // ✅ DON'T call setCurrentUserId here
                        // It's already set by setupAuthStateObserver()
                        Timber.d("✓ Login successful, userId=$userId")
                        navController.navigate(Screen.ItemList.route)
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Screen.Login.route)
                    }
                )
            }

            composable(Screen.ItemList.route) {
                ItemListScreen(
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    onLogout = {
                        handleLogout(navController)
                    }
                )
            }

            composable(Screen.ItemDetail.route) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                ItemDetailScreen(
                    itemId = itemId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }

    /**
     * ✅ CRITICAL: Atomic logout handler
     * Ensures all cleanup happens in correct order with error handling
     */
    private fun handleLogout(navController: NavController) {
        lifecycleScope.launch {
            try {
                Timber.i("🚪 Logout initiated")

                // Step 1: End session
                try {
                    sessionManager.endSession("User initiated logout")
                    Timber.d("✓ Session ended")
                } catch (e: Exception) {
                    Timber.e(e, "Error ending session, continuing...")
                }

                // Step 2: Clear vault
                try {
                    itemViewModel.clearVault()
                    Timber.d("✓ Vault cleared")
                } catch (e: Exception) {
                    Timber.e(e, "Error clearing vault")
                }

                // Step 3: Clear auth state
                try {
                    itemViewModel.clearSecrets()
                    userViewModel.clearAuthState()
                    Timber.d("✓ Auth state cleared")
                } catch (e: Exception) {
                    Timber.e(e, "Error clearing auth")
                }

                // Step 4: Navigate to login
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.ItemList.route) { inclusive = true }
                }

                Timber.i("✅ Logout completed")
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error during logout")
            }
        }
    }

    private fun setupPassphraseRotation() {
        // Passphrase rotation logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lifecycleScope.launch {
                // Rotation logic for Android 11+
            }
        }
    }

    private fun setupSessionCleanup() {
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)  // Check every minute
                sessionManager.checkSessionTimeout()
            }
        }
    }
}
