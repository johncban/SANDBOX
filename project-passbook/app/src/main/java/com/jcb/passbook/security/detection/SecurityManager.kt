package com.jcb.passbook.security.detection

import android.content.Context
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

/**
 * SecurityManager provides comprehensive runtime security monitoring.
 * 
 * THREAT DETECTION:
 * - Root/jailbreak detection via RootBeer + custom heuristics
 * - Debugging/instrumentation detection (ADB, debugger attachment)
 * - Emulator detection patterns
 * - Frida/dynamic analysis framework detection
 * - SELinux enforcement validation
 * - Runtime integrity checks
 * 
 * RESPONSE ACTIONS:
 * - Immediate session invalidation on compromise
 * - Security event audit logging
 * - Application termination for critical threats
 */
@Singleton
class SecurityManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val auditLogger: AuditLogger
) {
    
    companion object {
        // Observable state for UI components
        val isCompromised = mutableStateOf(false)
        
        private var periodicCheckJob: Job? = null
        private var auditLoggerInstance: AuditLogger? = null
        
        // Static methods for backward compatibility
        fun initializeAuditing(auditLogger: AuditLogger) {
            auditLoggerInstance = auditLogger
        }
        
        fun checkRootStatus(context: Context, onCompromised: () -> Unit) {
            if (isDeviceCompromised(context)) {
                isCompromised.value = true
                onCompromised()
            }
        }
        
        fun startPeriodicSecurityCheck(context: Context) {
            stopPeriodicSecurityCheck() // Ensure no duplicate jobs
            
            periodicCheckJob = CoroutineScope(Dispatchers.Default).launch {
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
                    } catch (e: Exception) {
                        Timber.e(e, "Error during periodic security check")
                        delay(60_000) // Back off on error
                    }
                }
            }
        }
        
        fun stopPeriodicSecurityCheck() {
            periodicCheckJob?.cancel()
            periodicCheckJob = null
        }
        
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
    }
    
    /**
     * Instance method for dependency-injected usage
     */
    suspend fun performSecurityCheck(context: Context): Boolean {
        val isCompromised = isDeviceCompromised(context)
        
        if (isCompromised) {
            // Immediate session invalidation on compromise
            try {
                if (sessionManager.isSessionActive()) {
                    sessionManager.endSession("Security compromise detected")
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
            }
            
            SecurityManager.isCompromised.value = true
        }
        
        return isCompromised
    }
    
    // ========== DETECTION METHODS ==========
    
    companion object DetectionMethods {
        
        private fun isDebuggerAttached(): Boolean {
            return try {
                Debug.isDebuggerConnected() || Debug.waitingForDebugger()
            } catch (e: Exception) {
                false
            }
        }
        
        private fun isAdbEnabled(context: Context): Boolean {
            return try {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED, 0
                ) != 0
            } catch (e: Exception) {
                false
            }
        }
        
        private fun isSELinuxPermissive(): Boolean {
            return try {
                val selinuxFile = File("/sys/fs/selinux/enforce")
                if (selinuxFile.exists()) {
                    val enforce = selinuxFile.readText().trim()
                    enforce != "1" // Should be "1" for enforcing mode
                } else {
                    // No SELinux file suggests compromised system
                    true
                }
            } catch (e: Exception) {
                // Assume compromised if can't read SELinux status
                true
            }
        }
        
        private fun isFridaDetected(): Boolean {
            return try {
                // Check for common Frida ports
                val fridaPorts = listOf(27042, 27043)
                
                fridaPorts.any { port ->
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", port), 100)
                            true // Port is open
                        }
                    } catch (e: Exception) {
                        false // Port not reachable
                    }
                } || 
                // Check for Frida libraries in memory maps
                checkFridaInMaps()
                
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                false
            }
        }
        
        private fun isEmulatorDetected(): Boolean {
            return try {
                // Common emulator indicators
                val build = android.os.Build
                
                // Check build properties
                (build.BRAND.startsWith("generic") && build.DEVICE.startsWith("generic")) ||
                build.FINGERPRINT.startsWith("generic") ||
                build.FINGERPRINT.startsWith("unknown") ||
                build.HARDWARE.contains("goldfish") ||
                build.HARDWARE.contains("vbox") ||
                build.MODEL.contains("google_sdk") ||
                build.MODEL.contains("Emulator") ||
                build.MODEL.contains("Android SDK built for x86") ||
                build.PRODUCT.contains("sdk_gphone") ||
                build.PRODUCT.contains("google_sdk") ||
                build.PRODUCT.contains("sdk") ||
                build.PRODUCT.contains("sdk_x86") ||
                build.PRODUCT.contains("vbox86p") ||
                build.SERIAL.equals("unknown", ignoreCase = true) ||
                build.SERIAL.equals("android", ignoreCase = true)
                
            } catch (e: Exception) {
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
                } catch (e: Exception) {
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
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}