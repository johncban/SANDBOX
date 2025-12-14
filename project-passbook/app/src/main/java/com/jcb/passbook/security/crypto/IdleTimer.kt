package com.jcb.passbook.security.crypto

import androidx.annotation.VisibleForTesting
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ✅ FIXED BUG-003: IdleTimer for Inactivity Tracking
 *
 * Tracks user inactivity and triggers callbacks when timeout is reached.
 * Resets on any user interaction (touch, key press, etc.).
 *
 * This prevents indefinite session access on stolen devices.
 *
 * Usage:
 * val idleTimer = IdleTimer(15 * 60 * 1000) // 15 min
 * idleTimer.onActivityDetected() // Reset timer on touch
 * idleTimer.start(object : IdleTimer.IdleCallback {
 *     override fun onIdleTimeout() {
 *         // Lock vault, clear keys, etc.
 *     }
 * })
 */
class IdleTimer(
    private val timeoutMillis: Long = 15 * 60 * 1000L // 15 minutes default
) {
    private val TAG = "IdleTimer"

    // State management
    private val lastActivityTime = AtomicLong(0L)
    private val isActive = AtomicBoolean(false)
    private var timeoutCallback: IdleCallback? = null
    private var timeoutRunnable: Runnable? = null

    // Handler for timeout scheduling
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        private const val ACTIVITY_GRACE_PERIOD = 500L // 500ms debounce
    }

    /**
     * Callback interface for idle timeout events
     */
    interface IdleCallback {
        fun onIdleTimeout()
    }

    /**
     * Start idle timer with callback
     */
    fun start(callback: IdleCallback) {
        Timber.tag(TAG).i("Starting idle timer (timeout: ${timeoutMillis}ms)")
        this.timeoutCallback = callback
        lastActivityTime.set(System.currentTimeMillis())
        isActive.set(true)
        scheduleTimeout()
    }

    /**
     * Stop idle timer
     */
    fun stop() {
        Timber.tag(TAG).i("Stopping idle timer")
        isActive.set(false)
        timeoutHandler.removeCallbacks(timeoutRunnable ?: Runnable {})
        lastActivityTime.set(0L)
    }

    /**
     * ✅ FIXED BUG-003: Record user activity
     * Call this on any user interaction (touch, key press, etc.)
     */
    fun onActivityDetected() {
        if (!isActive.get()) {
            return
        }

        val now = System.currentTimeMillis()
        val lastActivity = lastActivityTime.get()

        // Debounce: ignore activities within grace period
        if (now - lastActivity < ACTIVITY_GRACE_PERIOD) {
            return
        }

        Timber.tag(TAG).d("Activity detected - resetting timer")
        lastActivityTime.set(now)
        scheduleTimeout()
    }

    /**
     * Get time remaining until timeout
     */
    fun getTimeRemaining(): Long {
        if (!isActive.get()) {
            return 0L
        }

        val elapsed = System.currentTimeMillis() - lastActivityTime.get()
        return kotlin.math.max(0L, timeoutMillis - elapsed)
    }

    /**
     * Get inactivity duration
     */
    @VisibleForTesting
    fun getInactivityDuration(): Long {
        if (!isActive.get()) {
            return 0L
        }

        return System.currentTimeMillis() - lastActivityTime.get()
    }

    /**
     * Check if timer is active
     */
    fun isRunning(): Boolean = isActive.get()

    /**
     * Private: Schedule timeout callback
     */
    private fun scheduleTimeout() {
        // Cancel existing timeout
        timeoutRunnable?.let {
            timeoutHandler.removeCallbacks(it)
        }

        // Create new timeout runnable
        timeoutRunnable = Runnable {
            if (isActive.get()) {
                Timber.tag(TAG).w("⏱️ Idle timeout reached - triggering callback")
                timeoutCallback?.onIdleTimeout()
                isActive.set(false)
            }
        }

        // Schedule new timeout
        timeoutHandler.postDelayed(timeoutRunnable!!, timeoutMillis)
    }
}