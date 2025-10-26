package com.jcb.passbook

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.screens.auth.UnlockScreen
import com.jcb.passbook.presentation.ui.theme.PassBookTheme
import com.jcb.passbook.security.detection.RootDetector
import com.jcb.passbook.security.detection.SecurityManager
import com.jcb.passbook.security.session.SessionManager
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.auth.UnlockViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // Changed from ComponentActivity to FragmentActivity

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize security components
        val rootedOnStartup = RootDetector.isDeviceRooted(this)
        val isCompromised = SecurityManager.isCompromised

        // Initial security check
        SecurityManager.checkRootStatus(this) {
            // Security compromise detected - emergency lock and exit
            sessionManager.emergencyLock()
            finish()
        }

        // Lifecycle observer for session management and security checks
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Check for background timeout
                    if (!sessionManager.onAppForeground()) {
                        // Session was locked due to timeout - will be handled in navigation
                    }
                    // Start periodic security monitoring
                    SecurityManager.startPeriodicSecurityCheck(this) {
                        sessionManager.emergencyLock()
                        finish()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    sessionManager.onAppBackground()
                    SecurityManager.stopPeriodicSecurityCheck()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // Ensure session is locked when app is destroyed
                    sessionManager.lock(SessionManager.LockTrigger.PROCESS_DEATH)
                }
                else -> {}
            }
        }
        lifecycle.addObserver(lifecycleObserver)

        setContent {
            PassBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Observe security and session state
                    var shouldFinish by remember { mutableStateOf(false) }
                    val sessionState by sessionManager.sessionState.collectAsState()

                    // Handle security compromise
                    LaunchedEffect(rootedOnStartup, isCompromised.value) {
                        if (rootedOnStartup || isCompromised.value) {
                            shouldFinish = true
                        }
                    }

                    // Manage secure window flag based on session state
                    LaunchedEffect(sessionState) {
                        when (sessionState) {
                            SessionManager.SessionState.UNLOCKED -> {
                                // Enable secure flag to prevent screenshots
                                window.setFlags(
                                    WindowManager.LayoutParams.FLAG_SECURE,
                                    WindowManager.LayoutParams.FLAG_SECURE
                                )
                                sessionManager.enableSecureWindow()
                            }
                            SessionManager.SessionState.LOCKED -> {
                                // Disable secure flag when locked
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                sessionManager.disableSecureWindow()
                            }
                            SessionManager.SessionState.UNLOCKING -> {
                                // Keep secure flag disabled during unlock process
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }
                    }

                    if (shouldFinish) {
                        // Show security violation dialog before finishing
                        if (rootedOnStartup) {
                            RootedDeviceDialog { finish() }
                        } else {
                            CompromisedDeviceDialog { finish() }
                        }
                    } else {
                        AppNavHost(sessionState)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(sessionState: SessionManager.SessionState) {
    val navController = rememberNavController()
    val itemViewModel: ItemViewModel = viewModel()
    val userViewModel: UserViewModel = viewModel()
    val unlockViewModel: UnlockViewModel = viewModel()

    // Determine start destination based on session state
    val startDestination = when (sessionState) {
        SessionManager.SessionState.LOCKED -> Destinations.UNLOCK
        SessionManager.SessionState.UNLOCKING -> Destinations.UNLOCK
        SessionManager.SessionState.UNLOCKED -> {
            // Check if user is logged in
            if (userViewModel.isUserLoggedIn()) {
                Destinations.ITEMLIST
            } else {
                Destinations.LOGIN
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        
        // Vault unlock screen - always accessible when session is locked
        composable(Destinations.UNLOCK) {
            UnlockScreen(
                unlockViewModel = unlockViewModel,
                activity = navController.context as FragmentActivity,
                onUnlockSuccess = {
                    // Navigate based on login state
                    if (userViewModel.isUserLoggedIn()) {
                        navController.navigate(Destinations.ITEMLIST) {
                            popUpTo(Destinations.UNLOCK) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Destinations.LOGIN) {
                            popUpTo(Destinations.UNLOCK) { inclusive = true }
                        }
                    }
                },
                onExit = {
                    // Exit app if user chooses to exit from unlock screen
                    (navController.context as FragmentActivity).finish()
                }
            )
        }
        
        // Login screen - only accessible when session is unlocked but user not logged in
        composable(Destinations.LOGIN) {
            // Redirect to unlock if session is locked
            LaunchedEffect(sessionState) {
                if (sessionState != SessionManager.SessionState.UNLOCKED) {
                    navController.navigate(Destinations.UNLOCK) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                }
            }
            
            LoginScreen(
                userViewModel = userViewModel,
                itemViewModel = itemViewModel,
                onLoginSuccess = { userId ->
                    itemViewModel.setUserId(userId)
                    userViewModel.setUserId(userId)
                    userViewModel.saveLastLoggedInUserId(userId)
                    navController.navigate(Destinations.ITEMLIST) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Destinations.REGISTER) }
            )
        }
        
        // Registration screen
        composable(Destinations.REGISTER) {
            // Redirect to unlock if session is locked
            LaunchedEffect(sessionState) {
                if (sessionState != SessionManager.SessionState.UNLOCKED) {
                    navController.navigate(Destinations.UNLOCK) {
                        popUpTo(Destinations.REGISTER) { inclusive = true }
                    }
                }
            }
            
            RegistrationScreen(
                userViewModel = userViewModel,
                onRegistrationSuccess = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Item list screen - vault content, requires both session unlock and user login
        composable(Destinations.ITEMLIST) {
            // Redirect to unlock if session is locked
            LaunchedEffect(sessionState) {
                if (sessionState != SessionManager.SessionState.UNLOCKED) {
                    navController.navigate(Destinations.UNLOCK) {
                        popUpTo(Destinations.ITEMLIST) { inclusive = true }
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

private object Destinations {
    const val UNLOCK = "unlock"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ITEMLIST = "itemList"
}

@Composable
fun RootedDeviceDialog(onExit: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { androidx.compose.material3.Text("Root Detected") },
        text = { androidx.compose.material3.Text("This device appears to be rooted. For security reasons, the app will exit.") },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onExit) {
                androidx.compose.material3.Text("Exit")
            }
        },
        dismissButton = null
    )
}

@Composable
fun CompromisedDeviceDialog(onExit: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { androidx.compose.material3.Text("Security Warning") },
        text = { androidx.compose.material3.Text("This device or environment is compromised (rooted, tampered, or debugging detected). For security reasons, the app will exit.") },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onExit) {
                androidx.compose.material3.Text("Exit")
            }
        },
        dismissButton = null
    )
}
