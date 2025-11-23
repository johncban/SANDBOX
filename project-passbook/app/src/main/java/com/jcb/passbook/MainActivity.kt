package com.jcb.passbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.core.di.SecurityInitializer
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegistrationScreen
import com.jcb.passbook.presentation.ui.theme.PassBookTheme
import com.jcb.passbook.security.detection.RootDetector
import com.jcb.passbook.security.detection.SecurityManager
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var securityInitializer: SecurityInitializer

    @Inject
    lateinit var sessionManager: SessionManager

    // ✅ TESTING MODE: Set to true to enable visual security prompts instead of app exit
    private val TESTING_MODE = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize security system
        lifecycleScope.launch {
            if (!securityInitializer.initialize()) {
                // Handle security initialization failure
                if (!TESTING_MODE) {
                    finish()
                    return@launch
                }
            }
        }

        // Check if user needs to log in
        if (!sessionManager.isSessionActive()) {
            Timber.d("No active session - redirecting to login")
        } else {
            Timber.d("Active session detected - user can access vault")
        }

        // Persistent Compose states for security testing
        val rootedOnStartup = RootDetector.isDeviceRooted(this)
        val isCompromised = SecurityManager.isCompromised

        // Initial security check with onCompromised callback
        SecurityManager.checkRootStatus(
            context = this,
            onCompromised = {
                Timber.w("Security compromise detected during initial check")
            }
        )

        // Lifecycle observer for periodic checks
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    SecurityManager.startPeriodicSecurityCheck(this)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    SecurityManager.stopPeriodicSecurityCheck()
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
                    if (TESTING_MODE) {
                        // ✅ TESTING MODE: Show security status overlay with navigation
                        var showSecurityScreen by remember { mutableStateOf(true) }

                        if (showSecurityScreen) {
                            SecurityTestingScreen(
                                rootedOnStartup = rootedOnStartup,
                                isCompromised = isCompromised.value,
                                onContinue = {
                                    showSecurityScreen = false
                                }
                            )
                        } else {
                            AppNavHost()
                        }
                    } else {
                        // PRODUCTION MODE: Exit on security compromise
                        var shouldFinish by remember { mutableStateOf(false) }

                        LaunchedEffect(rootedOnStartup, isCompromised.value) {
                            if (rootedOnStartup || isCompromised.value) {
                                shouldFinish = true
                            }
                        }

                        if (shouldFinish) {
                            if (rootedOnStartup) {
                                RootedDeviceDialog { finish() }
                            } else {
                                CompromisedDeviceDialog { finish() }
                            }
                        } else {
                            AppNavHost()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SecurityManager.stopPeriodicSecurityCheck()
        SecurityManager.shutdown()
        securityInitializer.shutdown()
        super.onDestroy()
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
                        itemViewModel.setUserId(userId.toLong())
                        userViewModel.setUserId(userId.toLong())
                        navController.navigate(Destinations.ITEMLIST) {
                            popUpTo(Destinations.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Destinations.REGISTER)
                    }
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
                    onBackClick = {
                        navController.popBackStack()
                    }
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
}

// ✅ NEW: Testing mode screen with detailed security status
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTestingScreen(
    rootedOnStartup: Boolean,
    isCompromised: Boolean,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top app bar
        TopAppBar(
            title = { Text("Security Testing Mode") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (rootedOnStartup || isCompromised) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (rootedOnStartup || isCompromised) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (rootedOnStartup || isCompromised) {
                            Icons.Filled.Warning
                        } else {
                            Icons.Filled.CheckCircle
                        },
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = if (rootedOnStartup || isCompromised) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color(0xFF4CAF50)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (rootedOnStartup || isCompromised) {
                            "⚠️ SECURITY THREAT DETECTED"
                        } else {
                            "✅ Device Secure"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (rootedOnStartup || isCompromised) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color(0xFF4CAF50)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Testing Mode Active",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Detection results
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detection Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SecurityCheckItem(
                        label = "Root Detection (RootBeer)",
                        detected = rootedOnStartup,
                        description = if (rootedOnStartup) {
                            "Device appears to be rooted via RootBeer library"
                        } else {
                            "No root detected via RootBeer"
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SecurityCheckItem(
                        label = "Comprehensive Security Check",
                        detected = isCompromised,
                        description = if (isCompromised) {
                            "One or more security threats detected:\n" +
                                    "• Root binaries found\n" +
                                    "• Debugger attached\n" +
                                    "• ADB enabled\n" +
                                    "• SELinux permissive\n" +
                                    "• Frida detected\n" +
                                    "• Emulator detected\n" +
                                    "• Xposed framework present"
                        } else {
                            "All security checks passed"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ℹ️ Testing Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "In production mode, the app would exit immediately if security threats are detected. " +
                                "Testing mode allows you to see detection results and continue using the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue button (navigates to app)
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Continue to App (Testing Only)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { /* Could add detailed logs view */ }) {
                Text("View Detailed Logs")
            }
        }
    }
}

@Composable
fun SecurityCheckItem(
    label: String,
    detected: Boolean,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (detected) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                Color(0xFFE8F5E9)
            }
        ) {
            Text(
                text = if (detected) "DETECTED" else "CLEAN",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (detected) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color(0xFF2E7D32)
                }
            )
        }
    }
}

@Composable
fun RootedDeviceDialog(onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Root Detected") },
        text = { Text("This device appears to be rooted. For security reasons, the app will exit.") },
        confirmButton = {
            Button(onClick = onExit) {
                Text("Exit")
            }
        },
        dismissButton = null
    )
}

@Composable
fun CompromisedDeviceDialog(onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Security Warning") },
        text = { Text("This device or environment is compromised (rooted, tampered, or debugging detected). For security reasons, the app will exit.") },
        confirmButton = {
            Button(onClick = onExit) {
                Text("Exit")
            }
        },
        dismissButton = null
    )
}
