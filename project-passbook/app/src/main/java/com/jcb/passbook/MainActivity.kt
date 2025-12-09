package com.jcb.passbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
                    Scaffold { paddingValues ->  // ← Add this parameter
                        NavHost(
                            navController = navController,
                            startDestination = "login",
                            modifier = Modifier.padding(paddingValues)  // ← Use it here
                        ) {
                            composable("login") {
                                LoginScreen(
                                    userViewModel = userViewModel,
                                    onLoginSuccess = {
                                        navController.navigate("itemList") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    },
                                    onNavigateToRegister = {
                                        navController.navigate("register")
                                    }
                                )
                            }

                            composable("register") {
                                RegistrationScreen(
                                    userViewModel = userViewModel,
                                    onRegistrationSuccess = {
                                        navController.navigate("itemList") {
                                            popUpTo("register") { inclusive = true }
                                        }
                                    },
                                    onNavigateToLogin = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("itemList") {
                                ItemListScreen(
                                    onItemClick = {},
                                    onAddNewItem = {},
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
