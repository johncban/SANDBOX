package com.jcb.passbook.util.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import com.jcb.passbook.util.audit.AuditLogger
import com.scottyab.rootbeer.RootBeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Root Detection with comprehensive checks, user interaction options,
 * and detailed audit logging for security analysis.
 */
@Singleton
class EnhancedRootDetector @Inject constructor(
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TAG = "EnhancedRootDetector"
        
        // Root detection configuration - HARDCODED SECURITY DECISIONS
        private const val ALLOW_USER_OVERRIDE = true // Set to false for production
        private const val STRICT_MODE = false // Set to true for maximum security
        private const val AUTO_EXIT_ON_CRITICAL = true // Automatically exit on critical threats
        private const val SHOW_DETAILED_WARNINGS = true // Show technical details to user
    }

    data class RootDetectionResult(
        val isRooted: Boolean,
        val detectionMethods: List<String>,
        val severity: SecurityLevel,
        val allowUserOverride: Boolean,
        val userMessage: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class SecurityLevel(val priority: Int) {
        LOW(1),     // Minor indicators, might be false positive
        MEDIUM(2),  // Multiple indicators, likely rooted
        HIGH(3),    // Strong evidence of root access
        CRITICAL(4) // Definitive root access detected - immediate action required
    }

    /**
     * Comprehensive root detection with detailed logging and user interaction decisions
     */
    suspend fun performEnhancedRootDetection(context: Context): RootDetectionResult = withContext(Dispatchers.IO) {
        val detectionMethods = mutableListOf<String>()
        var isRooted = false
        var maxSeverity = SecurityLevel.LOW

        Timber.d("$TAG: Starting enhanced root detection with hardcoded security policies")
        auditLogger.logSecurityEvent(
            "Enhanced root detection initiated - ALLOW_OVERRIDE=$ALLOW_USER_OVERRIDE, STRICT=$STRICT_MODE",
            "INFO",
            AuditOutcome.SUCCESS
        )

        // 1. RootBeer Library Detection
        val rootBeerResult = detectWithRootBeer(context)
        if (rootBeerResult.first) {
            isRooted = true
            detectionMethods.addAll(rootBeerResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.HIGH)
        }

        // 2. Custom SU Binary Detection
        val suBinaryResult = detectSuBinaries()
        if (suBinaryResult.first) {
            isRooted = true
            detectionMethods.addAll(suBinaryResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.HIGH)
        }

        // 3. Root Management App Detection
        val rootAppsResult = detectRootManagementApps(context)
        if (rootAppsResult.first) {
            isRooted = true
            detectionMethods.addAll(rootAppsResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.MEDIUM)
        }

        // 4. System Properties Analysis
        val propsResult = analyzeDangerousProperties()
        if (propsResult.first) {
            isRooted = true
            detectionMethods.addAll(propsResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.MEDIUM)
        }

        // 5. File System Permissions Check
        val permissionsResult = checkFileSystemPermissions()
        if (permissionsResult.first) {
            isRooted = true
            detectionMethods.addAll(permissionsResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.CRITICAL)
        }

        // 6. Runtime Environment Analysis
        val runtimeResult = analyzeRuntimeEnvironment()
        if (runtimeResult.first) {
            isRooted = true
            detectionMethods.addAll(runtimeResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.HIGH)
        }

        // 7. Build Tags and Test Keys
        val buildTagsResult = checkBuildTags()
        if (buildTagsResult.first) {
            isRooted = true
            detectionMethods.addAll(buildTagsResult.second)
            maxSeverity = maxOf(maxSeverity, SecurityLevel.MEDIUM)
        }

        // HARDCODED SECURITY DECISIONS
        val allowOverride = determineUserOverridePolicy(maxSeverity)
        val userMessage = generateUserMessage(isRooted, maxSeverity, detectionMethods)

        val result = RootDetectionResult(
            isRooted = isRooted,
            detectionMethods = detectionMethods,
            severity = maxSeverity,
            allowUserOverride = allowOverride,
            userMessage = userMessage
        )

        // Log comprehensive results
        logRootDetectionResults(result)
        
        Timber.d("$TAG: Root detection completed - Rooted: $isRooted, Severity: $maxSeverity, Override: $allowOverride")
        
        result
    }

    /**
     * HARDCODED SECURITY DECISION: Determine if user override is allowed
     */
    private fun determineUserOverridePolicy(severity: SecurityLevel): Boolean {
        return when {
            STRICT_MODE -> false // No overrides in strict mode
            severity == SecurityLevel.CRITICAL -> false // Critical threats cannot be overridden
            AUTO_EXIT_ON_CRITICAL && severity >= SecurityLevel.HIGH -> false // Auto-exit on high/critical
            !ALLOW_USER_OVERRIDE -> false // Global override disabled
            else -> true // Allow override for low/medium severity
        }
    }

    /**
     * Generate appropriate user message based on detection results and security policies
     */
    private fun generateUserMessage(isRooted: Boolean, severity: SecurityLevel, methods: List<String>): String {
        if (!isRooted) {
            return "Device security check passed. No root access detected."
        }

        val baseMessage = when (severity) {
            SecurityLevel.LOW -> "Potential security risk detected. Some root indicators found."
            SecurityLevel.MEDIUM -> "Moderate security risk detected. Multiple root indicators found."
            SecurityLevel.HIGH -> "High security risk detected. Strong evidence of root access."
            SecurityLevel.CRITICAL -> "CRITICAL SECURITY THREAT: Definitive root access detected. Application will terminate for security."
        }

        return if (SHOW_DETAILED_WARNINGS && methods.isNotEmpty()) {
            val detectionSummary = methods.take(3).joinToString(", ")
            "$baseMessage\n\nDetection methods: $detectionSummary"
        } else {
            baseMessage
        }
    }

    /**
     * RootBeer library detection with detailed analysis
     */
    private fun detectWithRootBeer(context: Context): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        val rootBeer = RootBeer(context)
        
        return try {
            val isRooted = rootBeer.isRooted
            if (isRooted) {
                methods.add("RootBeer-Library-Detection")
                
                // Additional RootBeer specific checks
                if (rootBeer.isRootedWithoutBusyBoxCheck) methods.add("RootBeer-WithoutBusyBox")
                Timber.w("$TAG: RootBeer detected root access")
            }
            Pair(isRooted, methods)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: RootBeer detection failed")
            auditLogger.logSecurityEvent(
                "RootBeer detection error: ${e.message}",
                "WARNING",
                AuditOutcome.FAILURE
            )
            Pair(false, emptyList())
        }
    }

    /**
     * Comprehensive SU binary detection
     */
    private fun detectSuBinaries(): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        val suPaths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/app/Superuser.apk",
            "/system/bin/failsafe/su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu"
        )

        var found = false
        suPaths.forEach { path ->
            try {
                if (File(path).exists()) {
                    found = true
                    methods.add("SU-Binary-Found: $path")
                    Timber.w("$TAG: SU binary found at $path")
                }
            } catch (e: Exception) {
                Timber.d("$TAG: Cannot check SU path $path: ${e.message}")
            }
        }

        // Test SU command execution
        if (testSuCommandExecution()) {
            found = true
            methods.add("SU-Command-Executable")
        }

        return Pair(found, methods)
    }

    /**
     * Test if SU command can be executed
     */
    private fun testSuCommandExecution(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val result = process.waitFor()
            Timber.w("$TAG: SU command execution test result: $result")
            result == 0
        } catch (e: Exception) {
            Timber.d("$TAG: SU command test failed: ${e.message}")
            false
        }
    }

    /**
     * Detect root management applications
     */
    private fun detectRootManagementApps(context: Context): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )

        var found = false
        val packageManager = context.packageManager
        
        rootApps.forEach { packageName ->
            try {
                packageManager.getPackageInfo(packageName, 0)
                found = true
                methods.add("Root-App-Installed: $packageName")
                Timber.w("$TAG: Root management app found: $packageName")
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed - this is good
            } catch (e: Exception) {
                Timber.d("$TAG: Error checking for app $packageName: ${e.message}")
            }
        }

        return Pair(found, methods)
    }

    /**
     * Analyze dangerous system properties
     */
    @SuppressLint("PrivateApi")
    private fun analyzeDangerousProperties(): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0",
            "service.adb.root" to "1",
            "persist.sys.root_access" to listOf("1", "2", "3"),
            "ro.build.tags" to "test-keys"
        )

        var found = false
        dangerousProps.forEach { (prop, dangerousValues) ->
            try {
                val value = getSystemProperty(prop)
                if (value != null) {
                    val isDangerous = when (dangerousValues) {
                        is String -> value == dangerousValues
                        is List<*> -> dangerousValues.contains(value)
                        else -> false
                    }
                    
                    if (isDangerous) {
                        found = true
                        methods.add("Dangerous-Property: $prop=$value")
                        Timber.w("$TAG: Dangerous property found: $prop=$value")
                    }
                }
            } catch (e: Exception) {
                Timber.d("$TAG: Cannot read property $prop: ${e.message}")
            }
        }

        return Pair(found, methods)
    }

    /**
     * Check file system permissions for unusual access
     */
    private fun checkFileSystemPermissions(): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        val sensitiveFiles = arrayOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/sbin",
            "/etc"
        )

        var suspiciousPermissions = false
        sensitiveFiles.forEach { path ->
            try {
                val file = File(path)
                if (file.exists() && file.canWrite()) {
                    suspiciousPermissions = true
                    methods.add("Writable-System-Path: $path")
                    Timber.w("$TAG: Suspicious write permissions on $path")
                }
            } catch (e: Exception) {
                Timber.d("$TAG: Cannot check permissions for $path: ${e.message}")
            }
        }

        return Pair(suspiciousPermissions, methods)
    }

    /**
     * Analyze runtime environment for debugging/tampering
     */
    private fun analyzeRuntimeEnvironment(): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        var suspicious = false

        // Check for debugging
        if (android.os.Debug.isDebuggerConnected()) {
            suspicious = true
            methods.add("Debugger-Connected")
            Timber.w("$TAG: Debugger detected")
        }

        if (android.os.Debug.waitingForDebugger()) {
            suspicious = true
            methods.add("Waiting-For-Debugger")
            Timber.w("$TAG: Waiting for debugger")
        }

        // Check for Frida or other dynamic analysis tools
        if (checkForFrida()) {
            suspicious = true
            methods.add("Frida-Detection")
        }

        return Pair(suspicious, methods)
    }

    /**
     * Check build tags for test-keys
     */
    private fun checkBuildTags(): Pair<Boolean, List<String>> {
        val methods = mutableListOf<String>()
        var suspicious = false

        if (Build.TAGS != null && Build.TAGS.contains("test-keys")) {
            suspicious = true
            methods.add("Build-Tags-Test-Keys")
            Timber.w("$TAG: Build tagged with test-keys")
        }

        return Pair(suspicious, methods)
    }

    /**
     * Check for Frida dynamic analysis framework
     */
    private fun checkForFrida(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ps")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val processOutput = reader.readText()
            reader.close()
            
            val hasFrida = processOutput.contains("frida") || 
                          processOutput.contains("gum-js-loop") ||
                          processOutput.contains("gdbus")
            
            if (hasFrida) {
                Timber.w("$TAG: Frida framework detected")
            }
            
            hasFrida
        } catch (e: Exception) {
            Timber.d("$TAG: Frida detection failed: ${e.message}")
            false
        }
    }

    /**
     * Get system property value
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim()
            reader.close()
            if (value.isNullOrEmpty()) null else value
        } catch (e: Exception) {
            Timber.d("$TAG: Cannot get property $key: ${e.message}")
            null
        }
    }

    /**
     * Log comprehensive root detection results for security analysis
     */
    private suspend fun logRootDetectionResults(result: RootDetectionResult) {
        val detectionDetails = result.detectionMethods.joinToString(", ")
        
        auditLogger.logSecurityEvent(
            message = "ENHANCED ROOT DETECTION - Rooted: ${result.isRooted}, " +
                     "Severity: ${result.severity}, Override Allowed: ${result.allowUserOverride}, " +
                     "Methods: [$detectionDetails]",
            securityLevel = when (result.severity) {
                SecurityLevel.LOW -> "INFO"
                SecurityLevel.MEDIUM -> "WARNING" 
                SecurityLevel.HIGH -> "ELEVATED"
                SecurityLevel.CRITICAL -> "CRITICAL"
            },
            outcome = if (result.isRooted) AuditOutcome.WARNING else AuditOutcome.SUCCESS
        )

        // Log individual detection methods for detailed analysis
        result.detectionMethods.forEach { method ->
            auditLogger.logUserAction(
                userId = null,
                username = "SYSTEM",
                eventType = AuditEventType.SECURITY_EVENT,
                action = "Enhanced root detection method triggered",
                resourceType = "SECURITY_CHECK",
                resourceId = method,
                outcome = AuditOutcome.WARNING,
                securityLevel = "ELEVATED"
            )
        }

        // Log security policy decisions
        auditLogger.logUserAction(
            userId = null,
            username = "SYSTEM",
            eventType = AuditEventType.SECURITY_EVENT,
            action = "Security policy decision applied",
            resourceType = "SECURITY_POLICY",
            resourceId = "ROOT_DETECTION_POLICY",
            outcome = AuditOutcome.SUCCESS,
            securityLevel = "INFO"
        )
    }
}