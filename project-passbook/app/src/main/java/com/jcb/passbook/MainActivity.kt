package com.jcb.passbook

import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
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
import com.jcb.passbook.utils.memory.MemoryManager
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

    @Inject
    lateinit var memoryManager: MemoryManager

    private val userViewModel: UserViewModel by viewModels()
    private val itemViewModel: ItemViewModel by viewModels()

    // State tracking
    private var isDatabaseReady = mutableStateOf(false)
    private var savedUserId: Long = -1L
    private var wasAuthenticated: Boolean = false
    private var isAppInForeground = false

    // ✅ NEW: Lifecycle state flags
    private var isBeingDestroyed = false
    private var isWindowFocused = false
    private var hasInputChannel = false

    // ══════════════════════════════════════════════════════════════
    // ✅ FIX: onCreate with Proper Window Initialization
    // ══════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).d("onCreate called")

        // ✅ CRITICAL FIX: Set window flags BEFORE setContent
        configureWindow()

        enableEdgeToEdge()

        // Log memory state
        memoryManager.logMemoryStats()

        // Restore state
        restoreInstanceState(savedInstanceState)

        // Initialize database asynchronously
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initializeDatabaseAsync()
        } else {
            isDatabaseReady.value = true
        }

        // Observe lifecycle
        observeLifecycle()

        // Set content
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
                        DatabaseLoadingScreen()
                    }
                }
            }
        }

        // ✅ NEW: Post window setup after content is set
        window.decorView.post {
            hasInputChannel = true
            Timber.tag(TAG).d("Window setup complete, input channel ready")
        }
    }

    // ✅ NEW: Proper window configuration
    private fun configureWindow() {
        try {
            window.apply {
                // Enable hardware acceleration
                setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )

                // ✅ FIX: Ensure input method visibility
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            Timber.tag(TAG).d("Window configured successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error configuring window")
        }
    }

    private fun restoreInstanceState(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            savedUserId = it.getLong(KEY_USER_ID, -1L)
            wasAuthenticated = it.getBoolean(KEY_IS_AUTHENTICATED, false)
            if (savedUserId > 0 && wasAuthenticated) {
                Timber.tag(TAG).i("Restoring state: userId=$savedUserId, authenticated=$wasAuthenticated")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun initializeDatabaseAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                initializeDatabaseKey()
                withContext(Dispatchers.Main) {
                    isDatabaseReady.value = true
                    if (savedUserId > 0 && wasAuthenticated) {
                        userViewModel.setUserId(savedUserId)
                        itemViewModel.setUserId(savedUserId)
                        Timber.tag(TAG).i("✓ State restored after database initialization")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to initialize database")
                withContext(Dispatchers.Main) {
                    showErrorToast("Database initialization failed: ${e.message}")
                }
            }
        }
    }

    private fun observeLifecycle() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                isAppInForeground = true
                Timber.tag(TAG).d("App in STARTED state (foreground)")
            }
        }
    }

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

    @Composable
    private fun PassBookNavigation(
        userViewModel: UserViewModel,
        itemViewModel: ItemViewModel
    ) {
        val navController = rememberNavController()

        val startDestination = if (savedUserId > 0 && wasAuthenticated) {
            "itemList"
        } else {
            "login"
        }

        NavHost(navController = navController, startDestination = startDestination) {
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

                LaunchedEffect(currentUserId) {
                    if (currentUserId != -1L && itemUserId != currentUserId) {
                        itemViewModel.setUserId(currentUserId)
                        delay(50)
                        val verifiedUserId = itemViewModel.userId.value
                        Timber.tag(TAG).i("UserId synced: $verifiedUserId")
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
            Timber.tag(TAG).d("=== Initializing database key ===")

            val databaseKey = databaseKeyManager.getOrCreateDatabasePassphrase()
            if (databaseKey == null) {
                Timber.tag(TAG).e("CRITICAL: Failed to initialize database key")
                withContext(Dispatchers.Main) {
                    showErrorToast("Database initialization failed")
                }
                return
            }

            Timber.tag(TAG).i("✓ Database key verified (${databaseKey.size} bytes)")

            KeystorePassphraseManager.clearRotationFlag(this@MainActivity)

            try {
                withContext(Dispatchers.IO) {
                    database.openHelper.writableDatabase
                }
                Timber.tag(TAG).i("✓ Database accessible")
            } catch (dbError: Exception) {
                Timber.tag(TAG).e(dbError, "Database access test failed")
            }

            Timber.tag(TAG).d("=== Database initialization complete ===")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during database initialization")
            withContext(Dispatchers.Main) {
                showErrorToast("Initialization error: ${e.message}")
            }
        }
    }

    private fun showErrorToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentUserId = userViewModel.userId.value
        val isAuthenticated = currentUserId > 0

        if (isAuthenticated) {
            outState.putLong(KEY_USER_ID, currentUserId)
            outState.putBoolean(KEY_IS_AUTHENTICATED, true)
            Timber.tag(TAG).d("Saved state: userId=$currentUserId")
        }
    }

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

        // ✅ FIX: Only trigger GC if not being destroyed
        if (!isBeingDestroyed && !isFinishing && isAppInForeground) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    memoryManager.requestGarbageCollection()
                    Timber.tag(TAG).d("GC requested on pause")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error during pause cleanup")
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        Timber.tag(TAG).d("onStop - App moved to background")

        // ✅ FIX: Save state and clean up only if not finishing
        if (!isFinishing && !isBeingDestroyed) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    memoryManager.requestGarbageCollection()
                    memoryManager.logMemoryStats()
                    Timber.tag(TAG).d("Memory released on stop")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error during stop cleanup")
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ✅ CRITICAL FIX: Proper Destroy Handling with Cleanup
    // ══════════════════════════════════════════════════════════════
    override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy - isFinishing=$isFinishing")

        // ✅ NEW: Mark as being destroyed to prevent race conditions
        isBeingDestroyed = true
        hasInputChannel = false

        // ✅ FIX: Only close database if truly finishing
        if (isFinishing) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Timber.tag(TAG).d("Finishing - closing all connections")
                    sessionManager.endSession("Activity destroyed")

                    // ✅ NEW: Give time for pending operations
                    delay(150)

                    database.close()
                    Timber.tag(TAG).d("Database closed successfully")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error closing database")
                }
            }
        } else {
            Timber.tag(TAG).d("Not finishing - keeping database open for config change")
        }

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Timber.tag(TAG).w("onTrimMemory called with level: $level")

        // ✅ FIX: Only handle if not being destroyed
        if (!isBeingDestroyed) {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            memoryManager.requestGarbageCollection()
                            Timber.tag(TAG).i("Memory trimmed at level $level")
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error during memory trim")
                        }
                    }
                }
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            memoryManager.requestGarbageCollection()
                            Timber.tag(TAG).i("UI hidden, memory trimmed")
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Error during UI hidden cleanup")
                        }
                    }
                }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.tag(TAG).w("onLowMemory called - system critically low on memory")

        if (!isBeingDestroyed) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    memoryManager.requestGarbageCollection()
                    memoryManager.logMemoryStats()
                    Timber.tag(TAG).i("Emergency memory cleanup performed")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error during low memory cleanup")
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ✅ NEW: Handle Window Focus Changes
    // ══════════════════════════════════════════════════════════════
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isWindowFocused = hasFocus
        Timber.tag(TAG).d("Window focus changed: $hasFocus, hasInputChannel: $hasInputChannel")
    }

    // ✅ NEW: Handle attach/detach from window
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Timber.tag(TAG).d("Activity attached to window")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hasInputChannel = false
        Timber.tag(TAG).d("Activity detached from window")
    }
}
