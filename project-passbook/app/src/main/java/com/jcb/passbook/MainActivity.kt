// @/app/src/main/java/com/jcb/passbook/MainActivity.kt

package com.jcb.passbook

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.jcb.passbook.security.crypto.DatabaseKeyManager
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var databaseKeyManager: DatabaseKeyManager

    @Inject
    lateinit var database: AppDatabase

    private val userViewModel: UserViewModel by viewModels()
    private val itemViewModel: ItemViewModel by viewModels()

    // ✅ NEW: Track initialization state
    private var isDatabaseReady = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ CRITICAL FIX: Initialize database BEFORE setting content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lifecycleScope.launch(Dispatchers.IO) {
                initializeDatabaseKey()
                withContext(Dispatchers.Main) {
                    isDatabaseReady.value = true
                }
            }
        } else {
            isDatabaseReady.value = true
        }

        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // ✅ Show loading while database initializes
                    val dbReady by isDatabaseReady

                    if (dbReady) {
                        PassBookNavigation(
                            userViewModel = userViewModel,
                            itemViewModel = itemViewModel
                        )
                    } else {
                        // Show loading screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Initializing secure database...")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PassBookNavigation(
        userViewModel: UserViewModel,
        itemViewModel: ItemViewModel
    ) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "login") {
            composable("login") {
                LoginScreen(
                    userViewModel = userViewModel,
                    itemViewModel = itemViewModel,
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
                    itemViewModel = itemViewModel,
                    onRegisterSuccess = {
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
                val currentUserId by userViewModel.userId.collectAsState()
                val itemUserId by itemViewModel.userId.collectAsState()

                LaunchedEffect(currentUserId, itemUserId) {
                    Timber.tag("MainActivity\$Navigation").d(
                        "ItemList launched - UserViewModel userId: $currentUserId, ItemViewModel userId: $itemUserId"
                    )
                }

                LaunchedEffect(currentUserId) {
                    if (currentUserId != -1L && itemUserId != currentUserId) {
                        Timber.tag("MainActivity\$Navigation").i(
                            "Syncing ItemViewModel userId from $itemUserId to $currentUserId"
                        )
                        itemViewModel.setUserId(currentUserId)

                        delay(50)

                        val verifiedUserId = itemViewModel.userId.value
                        if (verifiedUserId == currentUserId) {
                            Timber.tag("MainActivity\$Navigation").i("✓ UserId sync verified: $verifiedUserId")
                        } else {
                            Timber.tag("MainActivity\$Navigation").e(
                                "CRITICAL: UserId sync failed! Expected: $currentUserId, Got: $verifiedUserId"
                            )
                        }
                    }
                }

                ItemListScreen(
                    itemViewModel = itemViewModel,
                    userViewModel = userViewModel,
                    navController = navController
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun initializeDatabaseKey() {
        try {
            Timber.d("=== Initializing database key ===")

            val databaseKey = databaseKeyManager.getOrCreateDatabasePassphrase()
            if (databaseKey == null) {
                Timber.e("CRITICAL: Failed to initialize database key")
                withContext(Dispatchers.Main) {
                    showErrorDialog("Database initialization failed. Please restart the app.")
                }
                return
            }
            Timber.i("✓ Database key verified (${databaseKey.size} bytes)")

            KeystorePassphraseManager.clearRotationFlag(this@MainActivity)
            Timber.d("✓ Cleared any stale rotation flags")

            try {
                withContext(Dispatchers.IO) {
                    database.openHelper.writableDatabase
                }
                Timber.i("✓ Database accessible")
            } catch (dbError: Exception) {
                Timber.e(dbError, "Database access test failed - may be normal on first launch")
            }

            Timber.d("=== Database initialization complete ===")

        } catch (e: Exception) {
            Timber.e(e, "Error during database initialization")
            withContext(Dispatchers.Main) {
                showErrorDialog("Initialization error: ${e.message}")
            }
        }
    }

    private fun showErrorDialog(message: String) {
        runOnUiThread {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setCancelable(false)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            sessionManager.endSession("Activity destroyed")
        }
    }
}
