package com.jcb.passbook.utils.memory

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "MemoryManager"

    /**
     * Get current memory information
     */
    fun getMemoryInfo(): ActivityManager.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    /**
     * Check if system is low on memory
     */
    fun isLowMemory(): Boolean {
        val memoryInfo = getMemoryInfo()
        return memoryInfo.lowMemory
    }

    /**
     * Get available memory percentage
     */
    fun getAvailableMemoryPercentage(): Float {
        val memoryInfo = getMemoryInfo()
        return (memoryInfo.availMem.toFloat() / memoryInfo.totalMem.toFloat()) * 100
    }

    /**
     * Log detailed memory statistics
     */
    fun logMemoryStats() {
        val memoryInfo = getMemoryInfo()
        val runtime = Runtime.getRuntime()

        Timber.tag(TAG).d("""
            ════════════════════════════════════════
            Memory Stats:
            ────────────────────────────────────────
            SYSTEM:
            - Available: ${memoryInfo.availMem / 1024 / 1024} MB
            - Total: ${memoryInfo.totalMem / 1024 / 1024} MB
            - Low Memory: ${memoryInfo.lowMemory}
            - Threshold: ${memoryInfo.threshold / 1024 / 1024} MB
            
            APP:
            - Max Heap: ${runtime.maxMemory() / 1024 / 1024} MB
            - Total Heap: ${runtime.totalMemory() / 1024 / 1024} MB
            - Free Heap: ${runtime.freeMemory() / 1024 / 1024} MB
            - Used Heap: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB
            ════════════════════════════════════════
        """.trimIndent())
    }

    /**
     * Request garbage collection
     */
    fun requestGarbageCollection() {
        try {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Timber.tag(TAG).d("Garbage collection requested")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error requesting GC")
        }
    }

    /**
     * Check if aggressive cleanup is needed
     */
    fun needsAggressiveCleanup(): Boolean {
        return getAvailableMemoryPercentage() < 20f || isLowMemory()
    }
}
