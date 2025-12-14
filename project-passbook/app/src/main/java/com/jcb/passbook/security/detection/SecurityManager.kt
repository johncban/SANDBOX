package com.jcb.passbook.security.detection

import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import com.jcb.passbook.security.audit.AuditLogger
import com.jcb.passbook.security.crypto.SessionManager
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val auditLogger: AuditLogger
) {

    /**
     * Instance method for dependency-injected usage
     * Performs security check and invalidates session if compromised
     */
    suspend fun performSecurityCheck(context: Context): Boolean {
        val isCompromised = isDeviceCompromised(context)

        if (isCompromised) {
            // Immediate session invalidation on compromise
            try {
                if (sessionManager.isSessionActive()) {
                    sessionManager.endSession()
                    auditLogger.logSecurityEvent(
                        "Session terminated due to security compromise",
                        "HIGH",
                        com.jcb.passbook.data.local.database.entities.AuditOutcome.SUCCESS
                    )
                }
            } catch (e: Exception) {
                auditLogger.logSecurityEvent(
                    "Failed to terminate session on compromise: ${e.message}",
                    "CRITICAL",
                    com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
                )
                Timber.e(e, "Error terminating session on security compromise")
            }

            Companion.isCompromised.value = true
        }

        return isCompromised
    }

    companion object {
        // Observable state for UI components
        val isCompromised = mutableStateOf(false)

        // ✅ FIXED: Use SupervisorJob to prevent child failures from cancelling parent
        private val securityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var periodicCheckJob: Job? = null
        private var auditLoggerInstance: AuditLogger? = null

        // ===== PUBLIC API FOR STATIC USAGE =====

        /**
         * Initialize auditing for static methods
         */
        fun initializeAuditing(auditLogger: AuditLogger) {
            auditLoggerInstance = auditLogger
        }

        /**
         * Check root status and invoke callback if compromised
         */
        fun checkRootStatus(context: Context, onCompromised: () -> Unit) {
            if (isDeviceCompromised(context)) {
                isCompromised.value = true
                onCompromised()
            }
        }

        /**
         * ✅ FIXED: Start periodic security checks with proper exception handling
         */
        fun startPeriodicSecurityCheck(context: Context) {
            stopPeriodicSecurityCheck() // Ensure no duplicate jobs

            periodicCheckJob = securityScope.launch {
                // ✅ FIXED: Add exception handler to prevent crashes
                val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                    when (exception) {
                        is CancellationException -> {
                            Timber.d("Security check job cancelled (normal app lifecycle)")
                        }
                        else -> {
                            Timber.e(exception, "Error during periodic security check")
                            auditLoggerInstance?.logSecurityEvent(
                                "Security check error: ${exception.message}",
                                "CRITICAL",
                                com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
                            )
                        }
                    }
                }

                withContext(exceptionHandler) {
                    while (isActive) {
                        try {
                            if (isDeviceCompromised(context)) {
                                isCompromised.value = true
                                auditLoggerInstance?.logSecurityEvent(
                                    "Periodic security check detected compromise",
                                    "CRITICAL",
                                    com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
                                )
                                break // Exit monitoring on compromise
                            }
                            delay(30_000) // Check every 30 seconds
                        } catch (e: CancellationException) {
                            // ✅ FIXED: Don't log cancellation as error (normal lifecycle)
                            Timber.d("Security monitoring cancelled")
                            throw e // Re-throw to properly cancel coroutine
                        } catch (e: Exception) {
                            Timber.e(e, "Error during periodic security check")
                            delay(60_000) // Back off on error
                        }
                    }
                }
            }
        }

        /**
         * ✅ FIXED: Stop periodic security checks safely
         */
        fun stopPeriodicSecurityCheck() {
            periodicCheckJob?.cancel()
            periodicCheckJob = null
        }

        /**
         * ✅ NEW: Clean up all resources when app is destroyed
         */
        fun shutdown() {
            try {
                stopPeriodicSecurityCheck()
                securityScope.cancel()
            } catch (e: Exception) {
                Timber.e(e, "Error during SecurityManager shutdown")
            }
        }

        // ===== PRIVATE DETECTION METHODS =====

        /**
         * Comprehensive device compromise detection.
         * Combines multiple heuristics for robust detection.
         */
        private fun isDeviceCompromised(context: Context): Boolean {
            return try {
                // Primary root detection via RootBeer
                val rootBeer = RootBeer(context)
                if (rootBeer.isRooted) {
                    auditLoggerInstance?.logSecurityEvent(
                        "Root detected via RootBeer",
                        "HIGH",
                        com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
                    )
                    return true
                }

                // Additional detection methods
                isDebuggerAttached() ||
                        isAdbEnabled(context) ||
                        isSELinuxPermissive() ||
                        isFridaDetected() ||
                        isEmulatorDetected() ||
                        hasRootBinaries() ||
                        hasXposedFramework()

            } catch (e: Exception) {
                Timber.e(e, "Error during compromise detection")
                // Fail-safe: assume compromised if detection fails
                auditLoggerInstance?.logSecurityEvent(
                    "Security detection failed: ${e.message}",
                    "CRITICAL",
                    com.jcb.passbook.data.local.database.entities.AuditOutcome.FAILURE
                )
                true
            }
        }

        // ... (rest of detection methods remain the same)

        private fun isDebuggerAttached(): Boolean {
            return try {
                Debug.isDebuggerConnected() || Debug.waitingForDebugger()
            } catch (_: Exception) {
                false
            }
        }

        private fun isAdbEnabled(context: Context): Boolean {
            return try {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED, 0
                ) != 0
            } catch (_: Exception) {
                false
            }
        }

        private fun isSELinuxPermissive(): Boolean {
            return try {
                val selinuxFile = File("/sys/fs/selinux/enforce")
                if (selinuxFile.exists()) {
                    val enforce = selinuxFile.readText().trim()
                    enforce != "1"
                } else {
                    true
                }
            } catch (_: Exception) {
                true
            }
        }

        private fun isFridaDetected(): Boolean {
            return try {
                val fridaPorts = listOf(27042, 27043)

                fridaPorts.any { port ->
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                            true
                        }
                    } catch (_: Exception) {
                        false
                    }
                } || checkFridaInMaps()

            } catch (_: Exception) {
                false
            }
        }

        private fun checkFridaInMaps(): Boolean {
            return try {
                val mapsFile = File("/proc/self/maps")
                if (mapsFile.exists()) {
                    val maps = mapsFile.readText()
                    maps.contains("frida", ignoreCase = true) ||
                            maps.contains("gum-js-loop", ignoreCase = true) ||
                            maps.contains("linjector", ignoreCase = true)
                } else false
            } catch (_: Exception) {
                false
            }
        }

        private fun isEmulatorDetected(): Boolean {
            return try {
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                        Build.FINGERPRINT.startsWith("generic") ||
                        Build.FINGERPRINT.startsWith("unknown") ||
                        Build.HARDWARE.contains("goldfish") ||
                        Build.HARDWARE.contains("vbox") ||
                        Build.MODEL.contains("google_sdk") ||
                        Build.MODEL.contains("Emulator") ||
                        Build.MODEL.contains("Android SDK built for x86") ||
                        Build.PRODUCT.contains("sdk_gphone") ||
                        Build.PRODUCT.contains("google_sdk") ||
                        Build.PRODUCT.contains("sdk") ||
                        Build.PRODUCT.contains("sdk_x86") ||
                        Build.PRODUCT.contains("vbox86p") ||
                        Build.SERIAL.equals("unknown", ignoreCase = true) ||
                        Build.SERIAL.equals("android", ignoreCase = true)

            } catch (_: Exception) {
                false
            }
        }

        private fun hasRootBinaries(): Boolean {
            val rootPaths = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/system/su",
                "/vendor/bin/su",
                "/system/bin/.ext/.su",
                "/system/bin/failsafe/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/cctalk",
                "/system/bin/joeykrim",
                "/system/app/Superuser.apk",
                "/system/etc/init.d/99SuperSUDaemon"
            )

            return rootPaths.any { path ->
                try {
                    File(path).exists()
                } catch (_: Exception) {
                    false
                }
            }
        }

        private fun hasXposedFramework(): Boolean {
            return try {
                val xposedPaths = arrayOf(
                    "/system/framework/XposedBridge.jar",
                    "/system/bin/app_process_xposed",
                    "/system/lib/libxposed_art.so",
                    "/system/lib64/libxposed_art.so"
                )

                xposedPaths.any { path ->
                    try {
                        File(path).exists()
                    } catch (_: Exception) {
                        false
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
