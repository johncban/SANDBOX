package com.jcb.passbook

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.jcb.passbook.presentation.ui.navigation.PassbookNavHost
import com.jcb.passbook.presentation.ui.theme.PassbookTheme
import com.jcb.passbook.presentation.viewmodel.shared.UserViewModel
import com.jcb.passbook.security.crypto.IdleTimer
import com.jcb.passbook.security.crypto.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity(), IdleTimer.IdleCallback {

    private val userViewModel: UserViewModel by viewModels()

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var idleTimer: IdleTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).i("MainActivity onCreate")

        idleTimer = IdleTimer(timeoutMillis = 15 * 60 * 1000L)

        sessionManager.setOnLogoutCallback {
            Timber.tag(TAG).i("Session ended callback - stopping idle timer")
            idleTimer.stop()
        }

        setContent {
            PassbookTheme {
                LaunchedEffect(Unit) {
                    Timber.tag(TAG).i("App launched")
                }

                PassbookNavHost(sessionManager = sessionManager)
            }
        }

    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            sessionManager.updateLastActivity()
            idleTimer.onActivityDetected()
        }
        return super.onTouchEvent(event)
    }

    override fun onIdleTimeout() {
        Timber.tag(TAG).w("⏱️ IDLE TIMEOUT - User inactive, locking vault")
        lifecycleScope.launch {
            sessionManager.endSession("Idle timeout")
        }
    }

    override fun onPause() {
        super.onPause()
        Timber.tag(TAG).d("MainActivity onPause - stopping idle timer")
        idleTimer.stop()
    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).d("MainActivity onResume - checking session state")

        if (sessionManager.isAuthenticated()) {
            idleTimer.start(this)
            Timber.tag(TAG).d("Session active - idle timer started")
        } else if (sessionManager.isSessionExpired()) {
            Timber.tag(TAG).w("Session expired while in background")
            lifecycleScope.launch {
                sessionManager.endSession("Session expired")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("MainActivity onDestroy - cleaning up")
        idleTimer.stop()

        if (isFinishing) {
            lifecycleScope.launch {
                sessionManager.endSession("Activity destroyed")
            }
        }
    }
}
