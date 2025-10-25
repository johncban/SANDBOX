package com.jcb.passbook


import android.os.Bundle
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
import com.jcb.passbook.presentation.ui.theme.PassBookTheme
import com.jcb.passbook.security.detection.RootDetector
import com.jcb.passbook.security.detection.SecurityManager
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // Changed from ComponentActivity to FragmentActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Persistent Compose states
        val rootedOnStartup = RootDetector.isDeviceRooted(this)
        val isCompromised = SecurityManager.isCompromised

        // Initial security check
        SecurityManager.checkRootStatus(this) {
            // Will update Compose observable state, handled in Composables' LaunchedEffect
        }

        // Lifecycle observer for periodic checks
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> SecurityManager.startPeriodicSecurityCheck(this)
                Lifecycle.Event.ON_PAUSE -> SecurityManager.stopPeriodicSecurityCheck()
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
                    // Observe rooted/security state
                    var shouldFinish by remember { mutableStateOf(false) }

                    LaunchedEffect(rootedOnStartup, isCompromised.value) {
                        if (rootedOnStartup || isCompromised.value) {
                            shouldFinish = true
                        }
                    }

                    if (shouldFinish) {
                        // Show the appropriate dialog before finishing
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
                    // Save the logged in user ID for biometric login
                    userViewModel.saveLastLoggedInUserId(userId)
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