package com.jcb.passbook

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jcb.passbook.presentation.navigation.AppNavigation
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Activity for Passbook
 * Fixed to use actual existing screens: ItemListScreen and ItemDetailsScreen
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val SESSION_TIMEOUT_MILLS: Long = 5 * 60 * 1000
        const val TAG = "MainActivity"
    }

    private var navController: NavController? = null
    private var sessionTimeoutJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val controller = rememberNavController()
                    navController = controller
                    AppNavigation(navController = controller)
                }
            }
        }

        setupSessionTimeout()
    }

    private fun setupSessionTimeout() {
        sessionTimeoutJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    delay(SESSION_TIMEOUT_MILLS)

                    navController?.navigate("login") {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }

                    Log.i(TAG, "✅ Session timeout triggered")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in session timeout: ${e.message}", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        navController = null
        Log.d(TAG, "✅ MainActivity destroyed")
    }
}
