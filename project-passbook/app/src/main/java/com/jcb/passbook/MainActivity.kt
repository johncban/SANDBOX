package com.jcb.passbook

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "MainActivity"
private const val KEY_USER_ID = "USER_ID"
private const val KEY_IS_AUTHENTICATED = "IS_AUTHENTICATED"
private const val KEY_CURRENT_ROUTE = "CURRENT_ROUTE"

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

    private var savedUserId: Long = -1L
    private var wasAuthenticated: Boolean = false
    private var savedRoute: String? = null
    private var isAppInForeground = false

    private var lifecycleJob: Job? = null
    private var cleanupJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).d("onCreate (savedInstanceState=${savedInstanceState != null})")

        try {
            configureWindowSafely()
            enableEdgeToEdge()
            restoreInstanceState(savedInstanceState)
            initializeLifecycleObserver()

            lifecycleScope.launch {
                delay(150)
                initializeUI()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in onCreate")
            finish()
        }
    }

    private fun configureWindowSafely() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            window.apply {
                setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            Timber.tag(TAG).d("Window configured successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error configuring window")
        }
    }

    private fun initializeUI() {
        setContent {
            PassbookTheme {
                MainContainer(
                    initialUserId = savedUserId,
                    initialAuthenticated = wasAuthenticated,
                    initialRoute = savedRoute,
                    onRouteChanged = { route ->
                        savedRoute = route
                    }
                )
            }
        }
        Timber.tag(TAG).i("MainActivity created successfully")
    }

    private fun restoreInstanceState(savedInstanceState: Bundle?) {
        savedInstanceState?.let { bundle ->
            try {
                savedUserId = bundle.getLong(KEY_USER_ID, -1L)
                wasAuthenticated = bundle.getBoolean(KEY_IS_AUTHENTICATED, false)
                savedRoute = bundle.getString(KEY_CURRENT_ROUTE)

                if (savedUserId > 0 && wasAuthenticated) {
                    userViewModel.setUserId(savedUserId)
                    itemViewModel.setUserId(savedUserId)
                    Timber.tag(TAG).i("State restored: userId=$savedUserId, route=$savedRoute")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error restoring state")
            }
        }
    }

    private fun initializeLifecycleObserver() {
        lifecycleJob?.cancel()
        lifecycleJob = lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                isAppInForeground = true
                Timber.tag(TAG).d("App in STARTED state (foreground)")
            }
        }
    }

    @Composable
    private fun MainContainer(
        initialUserId: Long,
        initialAuthenticated: Boolean,
        initialRoute: String?,
        onRouteChanged: (String) -> Unit
    ) {
        val navController = rememberNavController()
        var currentRoute by remember { mutableStateOf(initialRoute) }

        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        currentFocus?.clearFocus()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        navController.currentBackStackEntry?.destination?.route?.let { route ->
                            currentRoute = route
                            onRouteChanged(route)
                        }
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        Timber.tag(TAG).d("App is being destroyed")
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                PassbookNavigation(
                    navController = navController,
                    userViewModel = userViewModel,
                    itemViewModel = itemViewModel,
                    startRoute = determineStartRoute(initialUserId, initialAuthenticated, currentRoute),
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }

    private fun determineStartRoute(
        savedUserId: Long,
        wasAuthenticated: Boolean,
        savedRoute: String?
    ): String {
        return when {
            savedRoute != null && wasAuthenticated -> savedRoute
            savedUserId > 0 && wasAuthenticated -> "itemList"
            else -> "login"
        }
    }

    @Composable
    private fun PassbookNavigation(
        navController: NavHostController,
        userViewModel: UserViewModel,
        itemViewModel: ItemViewModel,
        startRoute: String,
        modifier: Modifier = Modifier
    ) {
        BackHandler(enabled = navController.currentBackStackEntry?.destination?.route == "itemList") {
            finish()
        }

        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = modifier
        ) {
            composable("login") {
                LoginScreen(
                    userViewModel = userViewModel,
                    itemViewModel = itemViewModel,
                    onLoginSuccess = {
                        navController.navigate("itemList") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate("register") {
                            launchSingleTop = true
                        }
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
                            launchSingleTop = true
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
                        Timber.tag(TAG).i("UserId synced: $currentUserId")
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
        try {
            val currentUserId = userViewModel.userId.value
            val isAuthenticated = currentUserId > 0

            if (isAuthenticated) {
                outState.putLong(KEY_USER_ID, currentUserId)
                outState.putBoolean(KEY_IS_AUTHENTICATED, true)
                savedRoute?.let {
                    outState.putString(KEY_CURRENT_ROUTE, it)
                }
                Timber.tag(TAG).d("Saved state: userId=$currentUserId, route=$savedRoute")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error saving state")
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        Timber.tag(TAG).d("onStart")
    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("onResume")
    }

    override fun onPause() {
        try {
            currentFocus?.clearFocus()
            window.decorView.clearFocus()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in onPause")
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false

        if (!isChangingConfigurations && !isFinishing) {
            cleanupJob?.cancel()
            cleanupJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(500)
                memoryManager.requestGarbageCollection()
            }
        }

        Timber.tag(TAG).d("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()

        Timber.tag(TAG).d("onDestroy - isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")

        try {
            currentFocus?.clearFocus()
            lifecycleJob?.cancel()
            cleanupJob?.cancel()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (isFinishing) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        sessionManager.endSession("Activity destroyed")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error during cleanup")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in onDestroy")
        }
    }
}
