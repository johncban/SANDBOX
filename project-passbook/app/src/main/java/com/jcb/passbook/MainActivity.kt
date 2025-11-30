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
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // CRITICAL FIX: Initialize database BEFORE any rotation attempts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lifecycleScope.launch(Dispatchers.IO) {
                initializeDatabaseAndHandleRotation()
            }
        }

        // Observe lifecycle for app close event
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
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
                    PassBookNavigation()
                }
            }
        }
    }

    @Composable
    private fun PassBookNavigation() {
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
                val userViewModel: UserViewModel = hiltViewModel()
                val itemViewModel: ItemViewModel = hiltViewModel()

                // ✅ SAFETY CHECK: Ensure ItemViewModel has userId
                LaunchedEffect(Unit) {
                    val currentUserId = userViewModel.userId.value
                    if (currentUserId != -1L && itemViewModel.userId.value == -1L) {
                        itemViewModel.setUserId(currentUserId)
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

    /**
     * CRITICAL FIX: Proper initialization sequence
     * 1. Ensure database key exists
     * 2. Verify database can be opened
     * 3. ONLY THEN check if rotation is needed
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun initializeDatabaseAndHandleRotation() {
        try {
            Timber.d("=== Starting database initialization sequence ===")

            // STEP 1: Ensure database key exists
            val databaseKey = databaseKeyManager.getOrCreateDatabasePassphrase()
            if (databaseKey == null) {
                Timber.e("CRITICAL: Failed to initialize database key")
                withContext(Dispatchers.Main) {
                    showErrorDialog("Database initialization failed. Please restart the app.")
                }
                return
            }
            Timber.i("✓ Database key verified (${databaseKey.size} bytes)")

            // STEP 2: Verify database is initialized before attempting to access it
            val isDatabaseInitialized = databaseKeyManager.isDatabaseKeyInitialized()
            if (!isDatabaseInitialized) {
                Timber.w("Database not yet initialized - skipping rotation")
                KeystorePassphraseManager.clearRotationFlag(this@MainActivity)
                return
            }

            // STEP 3: Test database access
            try {
                withContext(Dispatchers.IO) {
                    // Force database to open and verify it's accessible
                    database.openHelper.writableDatabase
                    // FIX: Try a simple query to ensure database is functional.
                    // This is a reliable way to check if the database is responsive without
                    // depending on a specific DAO method.
                    database.query("SELECT 1", null).close()
                }
                Timber.i("✓ Database opened and verified successfully")
            } catch (dbError: Exception) {
                Timber.e(dbError, "CRITICAL: Database access failed")
                withContext(Dispatchers.Main) {
                    showErrorDialog("Cannot access database: ${dbError.message}")
                }
                return
            }

            // STEP 4: NOW check if rotation is needed
            if (KeystorePassphraseManager.isRotationNeeded(this@MainActivity)) {
                Timber.i("Rotation is scheduled - performing passphrase rotation")

                // Verify we have a current passphrase BEFORE rotating
                val currentPassphrase = KeystorePassphraseManager.getCurrentPassphrase(this@MainActivity)
                if (currentPassphrase == null) {
                    Timber.w("No current passphrase found - skipping rotation (first launch)")
                    KeystorePassphraseManager.clearRotationFlag(this@MainActivity)
                    return
                }
                Timber.d("✓ Current passphrase verified")

                // Perform the rotation
                performPassphraseRotationOnOpen()
            } else {
                Timber.d("No passphrase rotation needed on app open")
            }

            Timber.d("=== Database initialization sequence complete ===")

        } catch (e: Exception) {
            Timber.e(e, "CRITICAL: Error during database initialization")
            withContext(Dispatchers.Main) {
                showErrorDialog("Initialization error: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun performPassphraseRotationOnOpen() {
        try {
            Timber.i("Performing passphrase rotation on app open")

            val backupSuccess = KeystorePassphraseManager.backupCurrentPassphrase(this)
            if (!backupSuccess) {
                Timber.e("Failed to backup current passphrase - aborting rotation")
                return
            }
            Timber.d("✓ Current passphrase backed up")

            // Generate new passphrase
            val newPassphrase = KeystorePassphraseManager.generateNewPassphrase()
            Timber.d("✓ New passphrase generated")

            // Commit new passphrase
            val success = KeystorePassphraseManager.commitNewPassphrase(this, newPassphrase)

            if (success) {
                Timber.i("Passphrase rotated successfully on app open")
                KeystorePassphraseManager.clearRotationFlag(this)
                KeystorePassphraseManager.clearBackup(this)
                Timber.d("✓ Rotation flag and backup cleared")
            } else {
                Timber.e("Failed to commit new passphrase - rolling back")
                KeystorePassphraseManager.rollbackToBackup(this)
                withContext(Dispatchers.Main) {
                    showErrorDialog("Key rotation failed. Using previous key.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during passphrase rotation on app open")
            // Attempt rollback
            try {
                KeystorePassphraseManager.rollbackToBackup(this)
                Timber.i("Rolled back to previous passphrase after error")
            } catch (rollbackError: Exception) {
                Timber.e(rollbackError, "CRITICAL: Rollback also failed")
                withContext(Dispatchers.Main) {
                    showErrorDialog("Critical error during key rotation. Data may be inaccessible.")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun schedulePassphraseRotationOnClose() {
        try {
            // Only schedule rotation if database is initialized
            val isInitialized = databaseKeyManager.isDatabaseKeyInitialized()
            if (isInitialized) {
                Timber.i("Scheduling passphrase rotation for next app open")
                KeystorePassphraseManager.markRotationNeeded(this)
            } else {
                Timber.d("Database not initialized - not scheduling rotation")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scheduling passphrase rotation")
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