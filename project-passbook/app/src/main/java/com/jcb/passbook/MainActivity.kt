package com.jcb.passbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.jcb.passbook.security.crypto.CryptoManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity with integrated CryptoManager testing via UI dialogs
 *
 * This approach avoids using Log.d() which may be blocked by root detection,
 * and instead displays test results in Compose dialogs.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
                        // Add test button overlay
                        MainContentWithCryptoTest()
                    }
                }
            }
        }
    }

    @Composable
    private fun MainContentWithCryptoTest() {
        var showTestDialog by remember { mutableStateOf(false) }
        var showTestButton by remember { mutableStateOf(true) }

        Box(modifier = Modifier.fillMaxSize()) {
            // Main app navigation
            AppNavHost()

            // Floating test button (shows on app launch)
            if (showTestButton) {
                FloatingActionButton(
                    onClick = { showTestDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text("🔒 Test", modifier = Modifier.padding(8.dp))
                }
            }

            // Test dialog
            if (showTestDialog) {
                CryptoManagerTestDialog(
                    onDismiss = {
                        showTestDialog = false
                        showTestButton = false // Hide button after first test
                    }
                )
            }
        }
    }

    @Composable
    private fun CryptoManagerTestDialog(onDismiss: () -> Unit) {
        var testResults by remember { mutableStateOf("Running tests...") }
        var isLoading by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            scope.launch {
                val results = runCryptoTests()
                testResults = results
                isLoading = false
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isLoading) onDismiss() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔐 CryptoManager Test Results")
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = testResults,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    enabled = !isLoading
                ) {
                    Text("Close")
                }
            }
        )
    }

    /**
     * Run all CryptoManager tests and return formatted results
     */
    private suspend fun runCryptoTests(): String = withContext(Dispatchers.Default) {
        val results = StringBuilder()
        results.appendLine("═══════════════════════════")
        results.appendLine("  CRYPTOMANAGER TEST SUITE")
        results.appendLine("═══════════════════════════\n")

        val crypto = CryptoManager()
        var passedTests = 0
        var totalTests = 0

        // Test 1: Argon2id Key Derivation
        totalTests++
        results.appendLine("━━━ Test 1: Argon2id Key Derivation ━━━")
        try {
            val password = "MySecurePassword123!".toByteArray()
            val salt = crypto.generateDerivationSalt()
            val startTime = System.currentTimeMillis()

            val key = crypto.deriveKeyArgon2id(password, salt)

            val elapsedTime = System.currentTimeMillis() - startTime

            if (key.size == 32) {
                results.appendLine("✅ PASS: Key derivation successful")
                results.appendLine("   Key size: ${key.size} bytes (256 bits)")
                results.appendLine("   Time: ${elapsedTime}ms")
                results.appendLine("   Key preview: ${key.take(8).joinToString("") { "%02x".format(it) }}...")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Invalid key size (${key.size} bytes)")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Test 2: AES-256-GCM Encryption/Decryption
        totalTests++
        results.appendLine("━━━ Test 2: AES-256-GCM Encryption ━━━")
        try {
            val plaintext = "Sensitive vault password data 🔐".toByteArray(Charsets.UTF_8)
            val key = crypto.generateRandomKey()
            val iv = crypto.generateGCMIV()

            val ciphertext = crypto.encryptAES256GCM(plaintext, key, iv)
            val decrypted = crypto.decryptAES256GCM(ciphertext, key, iv)

            if (plaintext.contentEquals(decrypted)) {
                results.appendLine("✅ PASS: Encryption/Decryption successful")
                results.appendLine("   Original: ${plaintext.size} bytes")
                results.appendLine("   Encrypted: ${ciphertext.size} bytes")
                results.appendLine("   Decrypted matches original: YES")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Decrypted data doesn't match")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Test 3: SHA-256 Hashing
        totalTests++
        results.appendLine("━━━ Test 3: SHA-256 Hashing ━━━")
        try {
            val data = "test data for hashing".toByteArray()
            val hash = crypto.hashSHA256(data)

            if (hash.size == 32) {
                results.appendLine("✅ PASS: SHA-256 hash successful")
                results.appendLine("   Hash size: ${hash.size} bytes")
                results.appendLine("   Hash: ${hash.joinToString("") { "%02x".format(it) }}")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Invalid hash size")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Test 4: HMAC-SHA256
        totalTests++
        results.appendLine("━━━ Test 4: HMAC-SHA256 ━━━")
        try {
            val data = "message to authenticate".toByteArray()
            val key = crypto.generateRandomKey()
            val hmac = crypto.hmacSHA256(data, key)

            if (hmac.size == 32) {
                results.appendLine("✅ PASS: HMAC-SHA256 successful")
                results.appendLine("   HMAC size: ${hmac.size} bytes")
                results.appendLine("   HMAC preview: ${hmac.take(8).joinToString("") { "%02x".format(it) }}...")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Invalid HMAC size")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Test 5: PBKDF2-SHA512 (Fallback)
        totalTests++
        results.appendLine("━━━ Test 5: PBKDF2-SHA512 Fallback ━━━")
        try {
            val password = "testPassword".toByteArray()
            val salt = crypto.generateDerivationSalt()
            val startTime = System.currentTimeMillis()

            val key = crypto.derivePBKDF2SHA512(password, salt)

            val elapsedTime = System.currentTimeMillis() - startTime

            if (key.size == 32) {
                results.appendLine("✅ PASS: PBKDF2 derivation successful")
                results.appendLine("   Key size: ${key.size} bytes")
                results.appendLine("   Time: ${elapsedTime}ms")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Invalid key size")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Test 6: Random Generation
        totalTests++
        results.appendLine("━━━ Test 6: Secure Random Generation ━━━")
        try {
            val random1 = crypto.generateRandomKey()
            val random2 = crypto.generateRandomKey()
            val iv = crypto.generateGCMIV()
            val salt = crypto.generateDerivationSalt()

            if (random1.size == 32 && random2.size == 32 &&
                !random1.contentEquals(random2) &&
                iv.size == 12 && salt.size == 16) {
                results.appendLine("✅ PASS: All random generation tests passed")
                results.appendLine("   Random keys are unique: YES")
                results.appendLine("   IV size correct (12 bytes): YES")
                results.appendLine("   Salt size correct (16 bytes): YES")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Random generation issue")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Test 7: Simplified Encrypt/Decrypt API
        totalTests++
        results.appendLine("━━━ Test 7: Simplified Encrypt/Decrypt ━━━")
        try {
            val plaintext = "Password123!".toByteArray()
            val key = crypto.generateRandomKey()

            val encrypted = crypto.encrypt(plaintext, key)
            val decrypted = crypto.decrypt(encrypted, key)

            if (plaintext.contentEquals(decrypted) && encrypted.size > plaintext.size) {
                results.appendLine("✅ PASS: Simplified API works correctly")
                results.appendLine("   IV automatically prepended: YES")
                results.appendLine("   Decryption successful: YES")
                passedTests++
            } else {
                results.appendLine("❌ FAIL: Simplified API issue")
            }
        } catch (e: Exception) {
            results.appendLine("❌ FAIL: ${e.message}")
        }
        results.appendLine()

        // Summary
        results.appendLine("═══════════════════════════")
        results.appendLine("  TEST SUMMARY")
        results.appendLine("═══════════════════════════")
        results.appendLine("Total Tests: $totalTests")
        results.appendLine("Passed: $passedTests")
        results.appendLine("Failed: ${totalTests - passedTests}")
        results.appendLine("Success Rate: ${(passedTests * 100) / totalTests}%")

        if (passedTests == totalTests) {
            results.appendLine("\n🎉 ALL TESTS PASSED! 🎉")
            results.appendLine("\nCryptoManager is working correctly!")
        } else {
            results.appendLine("\n⚠️ SOME TESTS FAILED ⚠️")
            results.appendLine("\nPlease review the failures above.")
        }

        return@withContext results.toString()
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

    @Composable
    fun RootedDeviceDialog(onExit: () -> Unit) {
        AlertDialog(
            onDismissRequest = {},
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
            onDismissRequest = {},
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
}