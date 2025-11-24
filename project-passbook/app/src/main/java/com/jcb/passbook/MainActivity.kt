package com.jcb.passbook

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.presentation.ui.screens.auth.LoginScreen
import com.jcb.passbook.presentation.ui.screens.auth.RegisterScreen
import com.jcb.passbook.presentation.ui.screens.vault.ItemListScreen
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.presentation.viewmodel.vault.ItemViewModel
import com.jcb.passbook.security.crypto.KeystorePassphraseManager
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var userViewModel: UserViewModel

    @Inject
    lateinit var itemViewModel: ItemViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ Rotate passphrase on app open (if needed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lifecycleScope.launch(Dispatchers.IO) {
                performPassphraseRotationOnOpen()
            } // ✅ FIXED: Added missing closing brace
        }

        // ✅ Observe lifecycle for app close event
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // App is going to background - schedule rotation for next open
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    schedulePassphraseRotationOnClose()
                }
            }
        })

        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    } // ✅ FIXED: Added missing closing brace for onCreate

    /**
     * ✅ FIXED: Extracted navigation to @Composable function
     */
    @Composable
    private fun AppNavigation() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "login"
        ) {
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
            } // ✅ FIXED: Added missing closing brace

            composable("register") {
                RegisterScreen(
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
            } // ✅ FIXED: Added missing closing brace

            composable("itemList") {
                ItemListScreen(
                    userViewModel = userViewModel,
                    itemViewModel = itemViewModel,
                    navController = navController
                )
            } // ✅ FIXED: Added missing closing brace
        } // ✅ FIXED: Added missing closing brace for NavHost
    } // ✅ FIXED: Added missing closing brace for AppNavigation

    /**
     * ✅ Rotate passphrase when app opens (if scheduled)
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun performPassphraseRotationOnOpen() {
        try {
            if (KeystorePassphraseManager.isRotationNeeded(this)) {
                Timber.i("🔄 Performing passphrase rotation on app open")

                // Generate new passphrase
                val newPassphrase = KeystorePassphraseManager.generateNewPassphrase()

                // Commit it (will be used by database on next initialization)
                val success = KeystorePassphraseManager.commitNewPassphrase(this, newPassphrase)

                if (success) {
                    Timber.i("✅ Passphrase rotated successfully on app open")
                    KeystorePassphraseManager.clearBackup(this)
                } else {
                    Timber.e("❌ Failed to rotate passphrase on app open")
                }
            } else {
                Timber.d("No passphrase rotation needed on app open")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during passphrase rotation on app open")
        }
    }

    /**
     * ✅ Schedule passphrase rotation for next app open
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.M)
    private fun schedulePassphraseRotationOnClose() {
        try {
            Timber.i("📅 Scheduling passphrase rotation for next app open")
            KeystorePassphraseManager.markRotationNeeded(this)
        } catch (e: Exception) {
            Timber.e(e, "Error scheduling passphrase rotation")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Ensure session is ended when activity is destroyed
        lifecycleScope.launch {
            sessionManager.endSession("Activity destroyed")
        }
    }
} // ✅ FIXED: Added missing closing brace for MainActivity class
