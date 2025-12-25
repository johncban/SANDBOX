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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.jcb.passbook.presentation.navigation.PassbookNavigation
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.presentation.viewmodel.main.MainViewModel
import com.jcb.passbook.ui.theme.PassbookTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Session timeout constant
    private companion object {
        const val SESSION_TIMEOUT_MILLS: Long = 5 * 60 * 1000 // 5 minutes
        const val TAG = "MainActivity"
    }

    private var navController: NavController? = null
    private var cleanupJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PassbookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PassbookNavigation(
                        onNavControllerCreated = { controller ->
                            navController = controller
                        }
                    )
                }
            }
        }

        // Setup session timeout
        setupSessionTimeout()
    }

    /**
     * Setup session timeout to clear vault after inactivity
     * Fixes: P2 - Memory leak (improper job management)
     */
    private fun setupSessionTimeout() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    delay(SESSION_TIMEOUT_MILLS)

                    // Get MainViewModel to access logout functionality
                    val mainViewModel: MainViewModel = hiltViewModel()
                    mainViewModel.logout()

                    Log.i(TAG, "✅ Session timeout triggered - user logged out")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in session timeout: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Proper cleanup on activity destroy
     * Fixes: P2 - Memory leak (missing cleanup)
     */
    override fun onDestroy() {
        super.onDestroy()

        // Cancel cleanup job to prevent memory leaks
        cleanupJob?.cancel()
        cleanupJob = null

        // Clear navigation controller reference
        navController = null

        Log.d(TAG, "✅ MainActivity destroyed - resources cleaned up")
    }

    /**
     * Handle lifecycle pause
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity paused")
    }

    /**
     * Handle lifecycle resume
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity resumed")
    }
}
