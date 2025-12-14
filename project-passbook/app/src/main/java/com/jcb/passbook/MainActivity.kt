package com.jcb.passbook

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.jcb.passbook.presentation.ui.navigation.PassbookNavHost
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.security.crypto.IdleTimer
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "MainActivity"

/**
 * ✅ FIXED: MainActivity with proper Hilt injection and navigation
 *
 * Architecture:
 * - SessionManager injected via Hilt @Inject
 * - PassbookNavHost handles its own dependencies via Hilt
 * - Idle timer integrated with session lifecycle
 * - Proper cleanup in lifecycle methods
 *
 * Fixes Applied:
 * - BUG-008: Removed sessionManager parameter from PassbookNavHost call
 * - BUG-009: Removed unused userViewModel property
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), IdleTimer.IdleCallback {

    /**
     * ✅ SessionManager injected via Hilt
     * Used for:
     * - Session lifecycle management
     * - Activity detection tracking
     * - Logout callbacks
     */
    @Inject
    lateinit var sessionManager: SessionManager

    /**
     * Idle timer for automatic session timeout
     * Default: 15 minutes of inactivity
     */
    private lateinit var idleTimer: IdleTimer

    // ========== LIFECYCLE METHODS ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).i("🚀 MainActivity onCreate")

        // Initialize idle timer (15 minutes timeout)
        idleTimer = IdleTimer(timeoutMillis = 15 * 60 * 1000L)

        // Setup logout callback to stop idle timer
        sessionManager.setOnLogoutCallback {
            Timber.tag(TAG).i("🔒 Session ended callback - stopping idle timer")
            idleTimer.stop()

            // Optional: Force navigate to login (handled by PassbookNavHost)
            // This ensures UI reflects logged-out state
        }

        setContent {
            PassbookTheme {
                // ✅ App launch tracking
                LaunchedEffect(Unit) {
                    Timber.tag(TAG).i("✅ App UI launched successfully")
                }

                // ✅ Session state observation for idle timer control
                val isSessionActive by sessionManager.isSessionActive.collectAsState()

                LaunchedEffect(isSessionActive) {
                    if (isSessionActive) {
                        Timber.tag(TAG).d("▶️ Session active - starting idle timer")
                        idleTimer.start(this@MainActivity)
                    } else {
                        Timber.tag(TAG).d("⏸️ Session inactive - stopping idle timer")
                        idleTimer.stop()
                    }
                }

                /**
                 * ✅ FIXED (BUG-008): PassbookNavHost called WITHOUT sessionManager parameter
                 *
                 * PassbookNavHost now manages its own dependencies via Hilt:
                 * - SessionManager injected into LoginScreen
                 * - RegistrationScreen handles its own injection
                 * - No prop drilling needed
                 */
                PassbookNavHost()
            }
        }
    }

    // ========== TOUCH INTERACTION TRACKING ==========

    /**
     * Track user touch interactions to reset idle timer
     * Called on ANY touch event in the activity
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            // Update session activity timestamp
            sessionManager.updateLastActivity()

            // Reset idle timer countdown
            idleTimer.onActivityDetected()

            Timber.tag(TAG).v("👆 User interaction detected")
        }

        return super.onTouchEvent(event)
    }

    // ========== IDLE TIMEOUT CALLBACK ==========

    /**
     * ✅ Callback triggered when user is inactive for 15 minutes
     * Automatically locks the vault and ends session
     */
    override fun onIdleTimeout() {
        Timber.tag(TAG).w("⏱️ IDLE TIMEOUT - User inactive for 15 minutes, locking vault")

        lifecycleScope.launch {
            try {
                sessionManager.endSession("Idle timeout - 15 minutes inactivity")
                Timber.tag(TAG).i("✅ Session ended due to idle timeout")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "❌ Error ending session on idle timeout")
            }
        }
    }

    // ========== LIFECYCLE - PAUSE/RESUME ==========

    /**
     * ⏸️ App moved to background
     * Stop idle timer to avoid unnecessary callbacks
     */
    override fun onPause() {
        super.onPause()
        Timber.tag(TAG).d("⏸️ MainActivity onPause - stopping idle timer")
        idleTimer.stop()
    }

    /**
     * ▶️ App returned to foreground
     * Check session validity and restart idle timer if needed
     */
    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("▶️ MainActivity onResume - checking session state")

        // Check if session is still valid
        if (sessionManager.isAuthenticated()) {
            idleTimer.start(this)
            Timber.tag(TAG).d("✅ Session active - idle timer restarted")

        } else if (sessionManager.isSessionExpired()) {
            // Session expired while app was in background
            Timber.tag(TAG).w("⚠️ Session expired while in background - ending session")

            lifecycleScope.launch {
                try {
                    sessionManager.endSession("Session expired in background")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "❌ Error ending expired session")
                }
            }
        } else {
            Timber.tag(TAG).d("ℹ️ No active session - user needs to login")
        }
    }

    // ========== LIFECYCLE - DESTROY ==========

    /**
     * 🧹 Activity being destroyed
     * Clean up resources and optionally end session
     */
    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("🧹 MainActivity onDestroy - cleaning up")

        // Always stop idle timer
        idleTimer.stop()

        // End session ONLY if activity is finishing (not config change)
        if (isFinishing) {
            Timber.tag(TAG).i("🚪 Activity finishing - ending session")

            lifecycleScope.launch {
                try {
                    sessionManager.endSession("Activity destroyed - app closed")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "❌ Error ending session on destroy")
                }
            }
        } else {
            Timber.tag(TAG).d("🔄 Configuration change detected - preserving session")
        }
    }
}
