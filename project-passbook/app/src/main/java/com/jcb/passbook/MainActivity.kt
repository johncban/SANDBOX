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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

private const val TAG = "MainActivity"
private const val KEY_USER_ID = "USER_ID"
private const val KEY_IS_AUTHENTICATED = "IS_AUTHENTICATED"

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

    // Track initialization state
    private var isDatabaseReady = mutableStateOf(false)
    private var savedUserId: Long = -1L
    private var wasAuthenticated: Boolean = false
    private var isAppInForeground = false

    // ══════════════════════════════════════════════════════════════
    // Activity Lifecycle - onCreate
    // ══════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).d("onCreate called")
        enableEdgeToEdge()

        // ✅ Restore state from process death
        savedInstanceState?.let {
            savedUserId = it.getLong(KEY_USER_ID, -1L)
            wasAuthenticated = it.getBoolean(KEY_IS_AUTHENTICATED, false)
            if (savedUserId > 0 && wasAuthenticated) {
                Timber.tag(TAG).i("Restoring state: userId=$savedUserId, authenticated=$wasAuthenticated")
            }
        }

        // ✅ Initialize database asynchronously
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lifecycleScope.launch(Dispatchers.IO) {
                initializeDatabaseKey()
                withContext(Dispatchers.Main) {
                    isDatabaseReady.value = true
                    // Restore state after database is ready
                    if (savedUserId > 0 && wasAuthenticated) {
                        userViewModel.setUserId(savedUserId)
                        itemViewModel.setUserId(savedUserId)
                        Timber.tag(TAG).i("✓ State restored after database initialization")
                    }
                }
            }
        } else {
            isDatabaseReady.value = true
        }

        // ✅ Observe lifecycle for foreground/background state
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                isAppInForeground = true
                Timber.tag(TAG).d("App in STARTED state (foreground)")
            }
        }

        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val dbReady by isDatabaseReady
                    if (dbReady) {
                        PassBookNavigation(
                            userViewModel = userViewModel,
                            itemViewModel = itemViewModel
                        )
                    } else {
                        // Show loading screen during initialization
                        DatabaseLoadingScreen()
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Database Loading Screen
    // ══════════════════════════════════════════════════════════════
    @Composable
    private fun DatabaseLoadingScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Initializing secure database...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Navigation Composable
    // ══════════════════════════════════════════════════════════════
    @Composable
    private fun PassBookNavigation(
        userViewModel: UserViewModel,
        itemViewModel: ItemViewModel
    ) {
        val navController = rememberNavController()

        // Determine start destination based on restored state
        val startDestination = if (savedUserId > 0 && wasAuthenticated) {
            "itemList"
        } else {
            "login"
        }

        NavHost(navController = navController, startDestination = startDestination) {
            // Login Screen
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

            // Registration Screen
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

            // Item List Screen (Vault)
            composable("itemList") {
                val currentUserId by userViewModel.userId.collectAsState()
                val itemUserId by itemViewModel.userId.collectAsState()

                // Sync userId between ViewModels
                LaunchedEffect(currentUserId, itemUserId) {
                    Timber.tag("$TAG\$Navigation").d(
                        "ItemList - UserViewModel userId: $currentUserId, ItemViewModel userId: $itemUserId"
                    )
                }

                LaunchedEffect(currentUserId) {
                    if (currentUserId != -1L && itemUserId != currentUserId) {
                        Timber.tag("$TAG\$Navigation").i(
                            "Syncing ItemViewModel userId from $itemUserId to $currentUserId"
                        )
                        itemViewModel.setUserId(currentUserId)
                        delay(50)
                        val verifiedUserId = itemViewModel.userId.value
                        if (verifiedUserId == currentUserId) {
                            Timber.tag("$TAG\$Navigation").i("✓ UserId sync verified: $verifiedUserId")
                        } else {
                            Timber.tag("$TAG\$Navigation").e(
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

    // ══════════════════════════════════════════════════════════════
    // Database Initialization
    // ══════════════════════════════════════════════════════════════
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun initializeDatabaseKey() {
        try {
            Timber.tag(TAG).d("=== Initializing database key ===")

            val databaseKey = databaseKeyManager.getOrCreateDatabasePassphrase()
            if (databaseKey == null) {
                Timber.tag(TAG).e("CRITICAL: Failed to initialize database key")
                withContext(Dispatchers.Main) {
                    showErrorDialog("Database initialization failed. Please restart the app.")
                }
                return
            }

            Timber.tag(TAG).i("✓ Database key verified (${databaseKey.size} bytes)")

            // Clear any stale rotation flags
            KeystorePassphraseManager.clearRotationFlag(this@MainActivity)
            Timber.tag(TAG).d("✓ Cleared any stale rotation flags")

            // Test database accessibility
            try {
                withContext(Dispatchers.IO) {
                    database.openHelper.writableDatabase
                }
                Timber.tag(TAG).i("✓ Database accessible")
            } catch (dbError: Exception) {
                Timber.tag(TAG).e(dbError, "Database access test failed - may be normal on first launch")
            }

            Timber.tag(TAG).d("=== Database initialization complete ===")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during database initialization")
            withContext(Dispatchers.Main) {
                showErrorDialog("Initialization error: ${e.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Error Dialog
    // ══════════════════════════════════════════════════════════════
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

    // ══════════════════════════════════════════════════════════════
    // ✅ FIXED: Process Death Handling - Save State
    // ══════════════════════════════════════════════════════════════
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current user ID if authenticated
        val currentUserId = userViewModel.userId.value
        val isAuthenticated = currentUserId > 0

        if (isAuthenticated) {
            outState.putLong(KEY_USER_ID, currentUserId)
            outState.putBoolean(KEY_IS_AUTHENTICATED, true)
            Timber.tag(TAG).d("Saved state: userId=$currentUserId, authenticated=true")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Process Death Handling - Restore State
    // ══════════════════════════════════════════════════════════════
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore user ID after process death
        val restoredUserId = savedInstanceState.getLong(KEY_USER_ID, -1L)
        val wasAuth = savedInstanceState.getBoolean(KEY_IS_AUTHENTICATED, false)

        if (restoredUserId > 0 && wasAuth) {
            // State will be restored in onCreate after database initialization
            Timber.tag(TAG).d("Preparing to restore: userId=$restoredUserId")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Lifecycle Callbacks
    // ══════════════════════════════════════════════════════════════
    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        Timber.tag(TAG).d("onStart - App visible to user")
    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("onResume - App in foreground")
    }

    override fun onPause() {
        super.onPause()
        Timber.tag(TAG).d("onPause - App losing focus")
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        Timber.tag(TAG).d("onStop - App moved to background")

        // ✅ FIX: Release resources when app goes to background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Trigger garbage collection suggestion
                System.gc()
                Timber.tag(TAG).d("Requested garbage collection")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during cleanup")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("onDestroy - Activity being destroyed")

        // ✅ FIX: Close database connections properly
        if (isFinishing) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    sessionManager.endSession("Activity destroyed")
                    database.close()
                    Timber.tag(TAG).d("Database closed successfully")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error closing database")
                }
            }
        }
    }
}
