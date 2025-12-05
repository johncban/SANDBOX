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

/**
 * ✅ CRITICAL FIX: Database now initialized by Hilt singleton
 * - Removed manual async DB initialization in activity
 * - Database is guaranteed ready when injected
 * - No more JobCancellationException on config changes
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var database: AppDatabase  // ✅ Now fully initialized by Hilt

    @Inject
    lateinit var memoryManager: MemoryManager

    private val userViewModel: UserViewModel by viewModels()
    private val itemViewModel: ItemViewModel by viewModels()

    // State tracking
    private var savedUserId: Long = -1L
    private var wasAuthenticated: Boolean = false
    private var isAppInForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).d("onCreate called")

        configureWindow()
        enableEdgeToEdge()

        // Restore state
        restoreInstanceState(savedInstanceState)

        // Observe lifecycle
        observeLifecycle()

        // ✅ Set content immediately - DB is already ready via Hilt
        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PassBookNavigation(
                        userViewModel = userViewModel,
                        itemViewModel = itemViewModel
                    )
                }
            }
        }
    }

    private fun configureWindow() {
        try {
            window.apply {
                setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )
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
                userViewModel.setUserId(savedUserId)
                itemViewModel.setUserId(savedUserId)
                Timber.tag(TAG).i("Restoring state: userId=$savedUserId")
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
                        Timber.tag(TAG).i("UserId synced: ${itemViewModel.userId.value}")
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
    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("onResume")
    }

    /**
     * ✅ CRITICAL FIX: Removed aggressive GC calls on lifecycle events
     * - Only trigger GC in background/low-memory scenarios
     * - Offloaded to IO dispatcher to prevent UI jank
     */
    override fun onPause() {
        super.onPause()
        // ✅ Removed GC call here - causes frame skips
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false

        // ✅ Only GC when truly backgrounded, not on config changes
        if (!isChangingConfigurations && !isFinishing) {
            lifecycleScope.launch(Dispatchers.IO) {
                memoryManager.requestGarbageCollection()
            }
        }
    }

    /**
     * ✅ CRITICAL FIX: Proper cleanup on destroy
     * - Only close DB if truly finishing (not config change)
     * - No manual DB lifecycle management needed with Hilt singleton
     */
    override fun onDestroy() {
        Timber.tag(TAG).d("onDestroy - isFinishing=$isFinishing")

        if (isFinishing) {
            lifecycleScope.launch(Dispatchers.IO) {
                sessionManager.endSession("Activity destroyed")
                // ✅ DB closure handled by Hilt - no manual close needed
            }
        }

        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    memoryManager.requestGarbageCollection()
                }
            }
        }
    }
}
