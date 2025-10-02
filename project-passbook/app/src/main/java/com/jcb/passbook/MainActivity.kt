package com.jcb.passbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.composables.ItemListScreen
import com.jcb.passbook.composables.firstscreen.LoginScreen
import com.jcb.passbook.composables.firstscreen.RegistrationScreen
import com.jcb.passbook.ui.theme.PassBookTheme
import com.jcb.passbook.util.logging.DebugLogger
import com.jcb.passbook.util.security.EnhancedSecurityManager
import com.jcb.passbook.util.security.SecurityDialog
import com.jcb.passbook.viewmodel.ItemViewModel
import com.jcb.passbook.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var enhancedSecurityManager: EnhancedSecurityManager
    
    @Inject
    lateinit var debugLogger: DebugLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize debug logging
        debugLogger.initialize(this)
        debugLogger.logInfo("MainActivity onCreate started")
        debugLogger.logSecurity("Application startup security check initiated", "INFO")

        // Enhanced security initialization
        var securityInitialized by mutableStateOf(false)
        var shouldExit by mutableStateOf(false)
        var userOverrodeFirstCheck by mutableStateOf(false)

        // Initialize security monitoring in coroutine
        LaunchedEffect(Unit) {
            try {
                debugLogger.logMethodEntry("initializeSecurityMonitoring", mapOf("context" to "MainActivity"))
                val startTime = System.currentTimeMillis()
                
                enhancedSecurityManager.initializeSecurityMonitoring(this@MainActivity)
                
                val endTime = System.currentTimeMillis()
                debugLogger.logPerformance("Security initialization", endTime - startTime)
                debugLogger.logSecurity("Enhanced security monitoring initialized successfully", "INFO")
                
                securityInitialized = true
                
                // Perform immediate security check
                val result = enhancedSecurityManager.performImmediateSecurityCheck(
                    context = this@MainActivity,
                    onSecurityThreat = { threatResult ->
                        debugLogger.logSecurity(
                            "Security threat detected in immediate check: ${threatResult.severity}",
                            threatResult.severity.name
                        )
                        
                        // Handle the threat through security manager
                        lifecycleScope.launch {
                            enhancedSecurityManager.handleSecurityThreat(
                                result = threatResult,
                                onUserOverride = {
                                    debugLogger.logSecurity("User overrode security warning", "WARNING")
                                    userOverrodeFirstCheck = true
                                },
                                onSecurityExit = {
                                    debugLogger.logSecurity("Security exit triggered", "CRITICAL")
                                    shouldExit = true
                                }
                            )
                        }
                    }
                )
                
                debugLogger.logSecurity(
                    "Initial security check completed - Rooted: ${result.isRooted}, Severity: ${result.severity}",
                    if (result.isRooted) "WARNING" else "INFO"
                )
                
            } catch (e: Exception) {
                debugLogger.logError("Failed to initialize security monitoring", e)
                debugLogger.logSecurity("Security initialization failed - CRITICAL", "CRITICAL")
                shouldExit = true
            }
        }

        // Lifecycle observer for security monitoring
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            debugLogger.logDebug("Lifecycle event: ${event.name}")
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    debugLogger.logSecurity("App resumed - performing security check", "INFO")
                    if (securityInitialized) {
                        lifecycleScope.launch {
                            enhancedSecurityManager.performImmediateSecurityCheck(
                                context = this@MainActivity,
                                onSecurityThreat = { threatResult ->
                                    enhancedSecurityManager.handleSecurityThreat(
                                        result = threatResult,
                                        onUserOverride = {
                                            debugLogger.logSecurity("User overrode security warning on resume", "WARNING")
                                        },
                                        onSecurityExit = {
                                            debugLogger.logSecurity("Security exit triggered on resume", "CRITICAL")
                                            shouldExit = true
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    debugLogger.logDebug("App paused")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    debugLogger.logInfo("MainActivity onDestroy")
                    enhancedSecurityManager.stopSecurityMonitoring()
                    debugLogger.close()
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
                    // Handle security exit
                    LaunchedEffect(shouldExit) {
                        if (shouldExit) {
                            debugLogger.logSecurity("App exiting due to security concerns", "CRITICAL")
                            delay(500) // Brief delay to ensure logging
                            finish()
                        }
                    }

                    // Show security dialog if needed
                    val dialogState = enhancedSecurityManager.getDialogState()
                    SecurityDialog(state = dialogState.value)

                    // Show main app content if security allows
                    if (securityInitialized && !shouldExit) {
                        AppNavHost()
                    } else if (!securityInitialized) {
                        // Show loading while security initializes
                        LoadingScreen()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        debugLogger.logInfo("MainActivity destroyed")
        enhancedSecurityManager.stopSecurityMonitoring()
        debugLogger.close()
    }
}

@Composable
private fun LoadingScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator()
            androidx.compose.material3.Text(
                text = "Initializing Security...",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val itemViewModel: ItemViewModel = viewModel()
    val userViewModel: UserViewModel = viewModel()

    NavHost(navController = navController, startDestination = Destinations.LOGIN) {
        composable(Destinations.LOGIN) {
            LoginScreen(
                userViewModel = userViewModel,
                itemViewModel = itemViewModel,
                onLoginSuccess = { userId ->
                    itemViewModel.setUserId(userId)
                    userViewModel.setUserId(userId)
                    navController.navigate(Destinations.ITEMLIST) {
                        popUpTo(Destinations.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Destinations.REGISTER) }
            )
        }
        composable(Destinations.REGISTER) {
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
        composable(Destinations.ITEMLIST) {
            ItemListScreen(
                itemViewModel = itemViewModel,
                userViewModel = userViewModel,
                navController = navController
            )
        }
    }
}

private object Destinations {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ITEMLIST = "itemList"
}

// Legacy dialogs for backwards compatibility (can be removed if not needed)
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