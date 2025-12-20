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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemDetailScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.presentation.viewmodel.ItemViewModel
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
 * Responsibilities:
 * - Host navigation graph with authentication and vault screens
 * - Manage application lifecycle (passphrase rotation, session cleanup)
 * - Inject SessionManager for global session state
 * - Provide ViewModels via Hilt integration
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    // ViewModels scoped to Activity lifecycle via Hilt
    private val userViewModel: UserViewModel by viewModels()
    private val itemViewModel: ItemViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupPassphraseRotation()
        setupSessionCleanup()

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

    @Composable
    private fun AppNavigation(
        userViewModel: UserViewModel,
        itemViewModel: ItemViewModel
    ) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Screen.Login.route
        ) {
            // Authentication flow
            composable(Screen.Login.route) {
                LoginScreen(
                    userViewModel = userViewModel,
                    onLoginSuccess = { userId ->
                        // Set userId in ItemViewModel for vault queries
                        itemViewModel.setCurrentUserId(userId)
                        navController.navigate(Screen.ItemList.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegistrationScreen(
                    userViewModel = userViewModel,
                    itemViewModel = itemViewModel,
                    onRegisterSuccess = { userId ->
                        itemViewModel.setCurrentUserId(userId)
                        navController.navigate(Screen.ItemList.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }

            // Vault flow
            composable(Screen.ItemList.route) {
                ItemListScreen(
                    viewModel = itemViewModel,
                    onItemClick = { item ->
                        navController.navigate(Screen.ItemDetail.createRoute(item.id))
                    },
                    onAddClick = {
                        navController.navigate(Screen.ItemDetail.createRoute(null))
                    }
                )
            }

            composable(
                route = Screen.ItemDetail.route,
                arguments = Screen.ItemDetail.arguments
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")?.toLongOrNull()
                ItemDetailScreen(
                    itemId = itemId,
                    viewModel = itemViewModel,
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
}

/**
 * Type-safe navigation routes with compile-time safety
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object ItemList : Screen("itemList")

    object ItemDetail : Screen("itemDetail/{itemId}") {
        const val ARG_ITEM_ID = "itemId"

        fun createRoute(itemId: Long?) = if (itemId == null) {
            "itemDetail/new"
        } else {
            "itemDetail/$itemId"
        }

        val arguments = listOf(
            navArgument(ARG_ITEM_ID) {
                type = NavType.StringType
                nullable = true
            }
        )
    }
}
