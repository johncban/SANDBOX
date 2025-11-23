package com.jcb.passbook.security.detection

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import timber.log.Timber

object RootDetector {

    /**
     * ✅ ENHANCED: Returns detailed root detection result with logging
     */
    fun isDeviceRooted(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        val isRooted = rootBeer.isRooted

        // Log detailed detection results for testing
        if (isRooted) {
            Timber.w("🚨 ROOT DETECTED via RootBeer library")
            logRootDetails(rootBeer)
        } else {
            Timber.i("✅ No root detected via RootBeer")
        }

        return isRooted
    }

    /**
     * ✅ NEW: Log detailed root detection information for debugging
     */
    private fun logRootDetails(rootBeer: RootBeer) {
        try {
            Timber.d("Root Detection Details:")
            Timber.d("  - Root management apps: ${rootBeer.detectRootManagementApps()}")
            Timber.d("  - Potentially dangerous apps: ${rootBeer.detectPotentiallyDangerousApps()}")
            Timber.d("  - Root cloaking apps: ${rootBeer.detectRootCloakingApps()}")
            Timber.d("  - Test keys: ${rootBeer.detectTestKeys()}")
            Timber.d("  - Busybox binary: ${rootBeer.checkForBusyBoxBinary()}")
            Timber.d("  - SU binary: ${rootBeer.checkForSuBinary()}")
            Timber.d("  - Dangerous properties: ${rootBeer.checkForDangerousProps()}")
            Timber.d("  - RW paths: ${rootBeer.checkForRWPaths()}")
            // ✅ REMOVED: detectMagiskBinary() - not available in RootBeer library
        } catch (e: Exception) {
            Timber.e(e, "Error logging root detection details")
        }
    }

    /**
     * ✅ NEW: Get human-readable summary of what was detected
     */
    fun getRootDetectionSummary(context: Context): String {
        val rootBeer = RootBeer(context)
        if (!rootBeer.isRooted) {
            return "Device is not rooted"
        }

        val detections = mutableListOf<String>()

        if (rootBeer.detectRootManagementApps()) detections.add("Root management apps")
        if (rootBeer.detectPotentiallyDangerousApps()) detections.add("Dangerous apps")
        if (rootBeer.detectRootCloakingApps()) detections.add("Root cloaking apps")
        if (rootBeer.checkForSuBinary()) detections.add("SU binary")
        if (rootBeer.checkForBusyBoxBinary()) detections.add("BusyBox")
        // ✅ REMOVED: detectMagiskBinary() - not available in RootBeer library

        return if (detections.isEmpty()) {
            "Root detected (method unknown)"
        } else {
            "Root detected via: ${detections.joinToString(", ")}"
        }
    }
}
