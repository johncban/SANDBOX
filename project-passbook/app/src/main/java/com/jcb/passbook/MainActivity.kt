package com.jcb.passbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.data.local.database.AppDatabase
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.vault.AddItemScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.security.crypto.SessionManager
import com.jcb.passbook.utils.memory.MemoryManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var memoryManager: MemoryManager

    private val userViewModel: UserViewModel by viewModels()
    private val itemViewModel: ItemViewModel by viewModels()

    private var isAppInForeground = false
    private var lifecycleJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).d("onCreate")

        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            enableEdgeToEdge()

            lifecycleScope.launch {
                delay(150)
                initializeUI()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in onCreate")
            finish()
        }
    }

    private fun initializeUI() {
        setContent {
            PassbookTheme {
                val navController = rememberNavController()

                Surface {
                    Scaffold { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = "login",
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            // ✅ LOGIN SCREEN
                            composable("login") {
                                LoginScreen(
                                    userViewModel = userViewModel,
                                    onLoginSuccess = {
                                        Timber.tag(TAG).i("✓ Login successful")
                                        // ✅ Navigate to itemList after successful login
                                        navController.navigate("itemList") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    },
                                    onNavigateToRegister = {
                                        navController.navigate("register")
                                    }
                                )
                            }

                            // ✅ REGISTRATION SCREEN
                            composable("register") {
                                RegistrationScreen(
                                    userViewModel = userViewModel,
                                    onRegistrationSuccess = {
                                        Timber.tag(TAG).i("✓ Registration successful")
                                        // ✅ Navigate to itemList after successful registration
                                        navController.navigate("itemList") {
                                            popUpTo("register") { inclusive = true }
                                        }
                                    },
                                    onNavigateToLogin = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            // ✅ ITEM LIST SCREEN
                            composable("itemList") {
                                ItemListScreen(
                                    onItemClick = { itemId ->
                                        Timber.tag(TAG).d("Item clicked: $itemId")
                                        // TODO: Navigate to ItemDetailScreen
                                    },
                                    onAddNewItem = {
                                        Timber.tag(TAG).d("FAB clicked - navigating to AddItemScreen")
                                        navController.navigate("addItem")
                                    },
                                    onBackClick = {
                                        lifecycleScope.launch {
                                            sessionManager.endSession("logout")
                                            navController.navigate("login") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }

                            // ✅ ADD ITEM SCREEN (NEW!)
                            composable("addItem") {
                                AddItemScreen(
                                    onBackClick = {
                                        navController.popBackStack()
                                    },
                                    onSuccess = {
                                        Timber.tag(TAG).i("✓ Item added successfully!")
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        Timber.tag(TAG).i("UI initialized")
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleJob?.cancel()
    }
}
