package com.jcb.passbook.security.detection

import android.content.Context
import android.os.Debug
import androidx.compose.runtime.mutableStateOf
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * SecurityManager: provides layered root, tampering and dynamic analysis detection.
 */
object SecurityManager {

    private const val TAG = "SecurityManager"
    private var rootDetected = false

    // Mutable state for Compose-friendly detection status
    val isCompromised = mutableStateOf(false)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


    lateinit var auditLogger: AuditLogger

    fun initializeAuditing(auditLogger: AuditLogger) {
        SecurityManager.auditLogger = auditLogger
    }

    /**
     * Run all root and tampering checks
     */
    fun checkRootStatus(context: Context, onCompromised: (() -> Unit)? = null) {
        coroutineScope.launch {
            val wasCompromised = isCompromised.value
            rootDetected = isDeviceRooted(context) || isDebuggable() || isFridaServerRunning() || hasSuspiciousProps()
            val detectedCompromised = rootDetected // could add other checks here

            if (detectedCompromised && !wasCompromised) {
                // Log the security event (auditLogger must be initialized)
                if (::auditLogger.isInitialized) {
                    auditLogger.logSecurityEvent(
                        "Device security compromise detected: root=${isDeviceRooted(context)}, " +
                                "debug=${isDebuggable()}, frida=${isFridaServerRunning()}, " +
                                "props=${hasSuspiciousProps()}",
                        "CRITICAL",
                        AuditOutcome.WARNING
                    )
                }
                withContext(Dispatchers.Main) {
                    isCompromised.value = true
                    onCompromised?.invoke()
                }
            } else {
                withContext(Dispatchers.Main) {
                    isCompromised.value = false
                }
            }
        }
    }

    /**
     * Periodically verify device integrity every given milliseconds (default 5 minutes)
     */
    fun startPeriodicSecurityCheck(
        context: Context,
        intervalMs: Long = 5 * 60 * 1000L,
        onCompromised: (() -> Unit)? = null
    ) {
        coroutineScope.launch {
            while (isActive) {
                checkRootStatus(context, onCompromised)
                delay(intervalMs)
            }
        }
    }

    fun stopPeriodicSecurityCheck() {
        coroutineScope.cancel()
    }

    /**
     * Check root status by RootBeer + custom checks
     */
    private fun isDeviceRooted(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        return rootBeer.isRooted || checkForSuBinary() || checkForSuperUserApk() || checkForDangerousProps()
    }

    private fun checkForSuBinary(): Boolean {
        val suLocations = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        return suLocations.any { File(it).exists() }
    }

    private fun checkForSuperUserApk(): Boolean {
        val superUserApkLocations = arrayOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperUser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/Kinguser.apk",
            "/system/app/Magisk.apk"
        )
        return superUserApkLocations.any { File(it).exists() }
    }

    private fun checkForDangerousProps(): Boolean {
        val props = arrayOf(
            "ro.debuggable",
            "ro.secure"
        )
        return props.any { getProp(it) == "1" }
    }

    private fun hasSuspiciousProps(): Boolean {
        val suspiciousProps = listOf(
            "ro.debuggable=1",
            "ro.secure=0",
            "service.adb.root=1",
            "persist.sys.root_access=1"
        )

        suspiciousProps.forEach { propCheck ->
            val keyVal = propCheck.split("=")
            if (keyVal.size == 2 && getProp(keyVal[0]) == keyVal[1]) {
                return true
            }
        }
        return false
    }

    private fun getProp(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()
            line
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detect if app is running under debugger
     */
    private fun isDebuggable(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Basic heuristic: check common Frida server ports and processes
     */
    private fun isFridaServerRunning(): Boolean {
        try {
            val processList = Runtime.getRuntime().exec("ps").inputStream.bufferedReader().use { it.readText() }
            if (processList.contains("frida") || processList.contains("gum-js-loop")) {
                return true
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    // TODO: Implement Play Integrity API integration here for your backend verification.

}