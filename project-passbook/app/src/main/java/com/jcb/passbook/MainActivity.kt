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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "MainActivity"

/**
 * ✅ FIXED: MainActivity with proper session management
 *
 * Fixes Applied:
 * - Added 30-second grace period before session expiration
 * - Prevents premature logout when app backgrounded briefly
 * - Proper idle timer lifecycle management
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), IdleTimer.IdleCallback {

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var idleTimer: IdleTimer
    private var backgroundTime: Long = 0L

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
        }

        setContent {
            PassbookTheme {
                LaunchedEffect(Unit) {
                    Timber.tag(TAG).i("✅ App UI launched successfully")
                }

                // Session state observation for idle timer control
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

                PassbookNavHost()
            }
        }
    }

    // ========== TOUCH INTERACTION TRACKING ==========

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            sessionManager.updateLastActivity()
            idleTimer.onActivityDetected()
            Timber.tag(TAG).v("👆 User interaction detected")
        }
        return super.onTouchEvent(event)
    }

    // ========== IDLE TIMEOUT CALLBACK ==========

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
     * ✅ CRITICAL FIX: App moved to background - record time but DON'T end session immediately
     *
     * Previous Bug: Session ended immediately on onPause(), losing user's session when:
     * - User switches to another app briefly
     * - User receives a phone call
     * - User opens notifications
     *
     * Fix: Record background time, idle timer paused but session preserved
     */
    override fun onPause() {
        super.onPause()
        backgroundTime = System.currentTimeMillis()
        Timber.tag(TAG).d("⏸️ MainActivity onPause - pausing idle timer (session preserved)")
        idleTimer.stop() // Pause timer but keep session alive
    }

    /**
     * ✅ CRITICAL FIX: App returned to foreground - check if grace period expired
     *
     * Grace Period: 30 seconds
     * - If backgrounded < 30s: Resume normally
     * - If backgrounded > 30s: End session for security
     */
    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("▶️ MainActivity onResume - checking session state")

        if (sessionManager.isAuthenticated()) {
            val timeInBackground = System.currentTimeMillis() - backgroundTime
            val GRACE_PERIOD_MS = 30_000L // 30 seconds

            if (backgroundTime > 0 && timeInBackground > GRACE_PERIOD_MS) {
                // ✅ Grace period expired - end session for security
                Timber.tag(TAG).w("⚠️ App was backgrounded for ${timeInBackground / 1000}s (> 30s grace period)")
                Timber.tag(TAG).w("🔒 Ending session for security")

                lifecycleScope.launch {
                    try {
                        sessionManager.endSession("Exceeded background grace period")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error ending session after grace period")
                    }
                }
            } else {
                // ✅ Within grace period - resume normally
                if (backgroundTime > 0) {
                    Timber.tag(TAG).d("✅ Within grace period (${timeInBackground / 1000}s) - resuming session")
                }
                idleTimer.start(this)
                Timber.tag(TAG).d("✅ Session active - idle timer restarted")
            }

            backgroundTime = 0L // Reset

        } else {
            Timber.tag(TAG).d("ℹ️ No active session - user needs to login")
        }
    }

    // ========== LIFECYCLE - DESTROY ==========

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("🧹 MainActivity onDestroy - cleaning up")

        idleTimer.stop()

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
