package com.jcb.passbook.util.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import com.jcb.passbook.util.audit.AuditLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Debug Logger with comprehensive security logging,
 * file output capabilities, and detailed system information capture.
 */
@Singleton
class DebugLogger @Inject constructor(
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TAG = "DebugLogger"
        
        // HARDCODED DEBUG CONFIGURATION
        private const val ENABLE_FILE_LOGGING = true
        private const val ENABLE_SYSTEM_INFO_LOGGING = true
        private const val ENABLE_SECURITY_DEBUG_LOGGING = true
        private const val MAX_LOG_FILE_SIZE_MB = 10
        private const val MAX_LOG_FILES = 5
        private const val LOG_FILE_PREFIX = "passbook_debug"
        
        // Log levels
        const val LEVEL_VERBOSE = 0
        const val LEVEL_DEBUG = 1
        const val LEVEL_INFO = 2
        const val LEVEL_WARN = 3
        const val LEVEL_ERROR = 4
        const val LEVEL_SECURITY = 5
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var logFileWriter: FileWriter? = null
    private var currentLogFile: File? = null
    private var isInitialized = false

    /**
     * Initialize debug logger with system information capture
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            if (ENABLE_FILE_LOGGING) {
                initializeFileLogging(context)
            }
            
            if (ENABLE_SYSTEM_INFO_LOGGING) {
                logSystemInformation(context)
            }
            
            isInitialized = true
            logInfo("DebugLogger initialized successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize debug logger")
        }
    }

    /**
     * Initialize file logging system
     */
    private fun initializeFileLogging(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            // Clean up old log files
            cleanupOldLogFiles(logDir)
            
            // Create new log file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            currentLogFile = File(logDir, "${LOG_FILE_PREFIX}_$timestamp.log")
            logFileWriter = FileWriter(currentLogFile, true)
            
            writeToFile("=== DEBUG LOG SESSION STARTED ===")
            writeToFile("Timestamp: ${dateFormatter.format(Date())}")
            writeToFile("App Version: ${getAppVersion(context)}")
            writeToFile("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            writeToFile("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            writeToFile("=========================================")
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize file logging")
        }
    }

    /**
     * Clean up old log files to maintain storage limits
     */
    private fun cleanupOldLogFiles(logDir: File) {
        try {
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: return
            
            // Remove files exceeding the limit
            if (logFiles.size >= MAX_LOG_FILES) {
                logFiles.drop(MAX_LOG_FILES - 1).forEach { file ->
                    file.delete()
                    Timber.d("$TAG: Deleted old log file: ${file.name}")
                }
            }
            
            // Check file sizes
            logFiles.forEach { file ->
                val sizeInMB = file.length() / (1024 * 1024)
                if (sizeInMB > MAX_LOG_FILE_SIZE_MB) {
                    file.delete()
                    Timber.d("$TAG: Deleted oversized log file: ${file.name} (${sizeInMB}MB)")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error cleaning up log files")
        }
    }

    /**
     * Log comprehensive system information for debugging
     */
    private fun logSystemInformation(context: Context) {
        scope.launch {
            try {
                val systemInfo = buildString {
                    appendLine("=== SYSTEM INFORMATION ===")
                    appendLine("Device Manufacturer: ${Build.MANUFACTURER}")
                    appendLine("Device Model: ${Build.MODEL}")
                    appendLine("Device Product: ${Build.PRODUCT}")
                    appendLine("Android Version: ${Build.VERSION.RELEASE}")
                    appendLine("API Level: ${Build.VERSION.SDK_INT}")
                    appendLine("Build ID: ${Build.ID}")
                    appendLine("Build Tags: ${Build.TAGS}")
                    appendLine("Build Type: ${Build.TYPE}")
                    appendLine("Fingerprint: ${Build.FINGERPRINT}")
                    appendLine("Board: ${Build.BOARD}")
                    appendLine("Bootloader: ${Build.BOOTLOADER}")
                    appendLine("Hardware: ${Build.HARDWARE}")
                    appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                    
                    // Memory information
                    val runtime = Runtime.getRuntime()
                    val maxMemory = runtime.maxMemory() / (1024 * 1024)
                    val totalMemory = runtime.totalMemory() / (1024 * 1024)
                    val freeMemory = runtime.freeMemory() / (1024 * 1024)
                    val usedMemory = totalMemory - freeMemory
                    
                    appendLine("Memory - Max: ${maxMemory}MB, Total: ${totalMemory}MB, Used: ${usedMemory}MB, Free: ${freeMemory}MB")
                    
                    // Storage information
                    val internalStorage = context.filesDir.usableSpace / (1024 * 1024)
                    appendLine("Internal Storage Available: ${internalStorage}MB")
                    
                    appendLine("=========================")
                }
                
                logInfo(systemInfo)
                
                auditLogger.logUserAction(
                    userId = null,
                    username = "SYSTEM",
                    eventType = AuditEventType.SYSTEM_EVENT,
                    action = "System information logged",
                    resourceType = "DEBUG_LOG",
                    resourceId = "SYSTEM_INFO",
                    outcome = AuditOutcome.SUCCESS,
                    securityLevel = "INFO"
                )
                
            } catch (e: Exception) {
                logError("Failed to log system information", e)
            }
        }
    }

    /**
     * Log verbose message
     */
    fun logVerbose(message: String, tag: String = TAG) {
        log(LEVEL_VERBOSE, tag, message)
    }

    /**
     * Log debug message
     */
    fun logDebug(message: String, tag: String = TAG) {
        log(LEVEL_DEBUG, tag, message)
    }

    /**
     * Log info message
     */
    fun logInfo(message: String, tag: String = TAG) {
        log(LEVEL_INFO, tag, message)
    }

    /**
     * Log warning message
     */
    fun logWarn(message: String, tag: String = TAG) {
        log(LEVEL_WARN, tag, message)
    }

    /**
     * Log error message
     */
    fun logError(message: String, throwable: Throwable? = null, tag: String = TAG) {
        val fullMessage = if (throwable != null) {
            "$message\nException: ${throwable.javaClass.simpleName}: ${throwable.message}\nStackTrace: ${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        log(LEVEL_ERROR, tag, fullMessage)
    }

    /**
     * Log security-related message with enhanced tracking
     */
    fun logSecurity(message: String, severity: String = "INFO", tag: String = TAG) {
        if (!ENABLE_SECURITY_DEBUG_LOGGING) return
        
        val securityMessage = "[SECURITY-$severity] $message"
        log(LEVEL_SECURITY, tag, securityMessage)
        
        // Also log to audit system for security events
        scope.launch {
            try {
                auditLogger.logSecurityEvent(
                    message = "Debug security log: $message",
                    securityLevel = severity,
                    outcome = when (severity) {
                        "CRITICAL", "HIGH" -> AuditOutcome.WARNING
                        "ERROR" -> AuditOutcome.FAILURE
                        else -> AuditOutcome.SUCCESS
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to log security event to audit system")
            }
        }
    }

    /**
     * Log method entry with parameters
     */
    fun logMethodEntry(methodName: String, params: Map<String, Any?> = emptyMap(), tag: String = TAG) {
        val paramString = if (params.isNotEmpty()) {
            params.entries.joinToString(", ") { "${it.key}=${it.value}" }
        } else {
            "no params"
        }
        logDebug("→ $methodName($paramString)", tag)
    }

    /**
     * Log method exit with result
     */
    fun logMethodExit(methodName: String, result: Any? = null, tag: String = TAG) {
        val resultString = result?.toString() ?: "void"
        logDebug("← $methodName returns: $resultString", tag)
    }

    /**
     * Log performance timing
     */
    fun logPerformance(operation: String, durationMs: Long, tag: String = TAG) {
        val level = when {
            durationMs > 5000 -> LEVEL_WARN
            durationMs > 1000 -> LEVEL_INFO
            else -> LEVEL_DEBUG
        }
        log(level, tag, "[PERFORMANCE] $operation took ${durationMs}ms")
    }

    /**
     * Core logging method
     */
    private fun log(level: Int, tag: String, message: String) {
        val timestamp = dateFormatter.format(Date())
        val levelString = when (level) {
            LEVEL_VERBOSE -> "V"
            LEVEL_DEBUG -> "D"
            LEVEL_INFO -> "I"
            LEVEL_WARN -> "W"
            LEVEL_ERROR -> "E"
            LEVEL_SECURITY -> "S"
            else -> "?"
        }
        
        val formattedMessage = "$timestamp [$levelString] $tag: $message"
        
        // Log to Timber (which goes to Android Log)
        when (level) {
            LEVEL_VERBOSE -> Timber.v(tag, message)
            LEVEL_DEBUG -> Timber.d(tag, message)
            LEVEL_INFO -> Timber.i(tag, message)
            LEVEL_WARN -> Timber.w(tag, message)
            LEVEL_ERROR -> Timber.e(tag, message)
            LEVEL_SECURITY -> Timber.w(tag, "[SECURITY] $message")
        }
        
        // Log to file if enabled
        if (ENABLE_FILE_LOGGING) {
            writeToFile(formattedMessage)
        }
    }

    /**
     * Write message to log file
     */
    private fun writeToFile(message: String) {
        scope.launch {
            try {
                logFileWriter?.let { writer ->
                    writer.appendLine(message)
                    writer.flush()
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to write to log file")
            }
        }
    }

    /**
     * Get current app version
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get log file for external access (debugging)
     */
    fun getCurrentLogFile(): File? = currentLogFile

    /**
     * Get all log files
     */
    fun getAllLogFiles(context: Context): List<File> {
        val logDir = File(context.filesDir, "logs")
        return logDir.listFiles { file ->
            file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Close logger and clean up resources
     */
    fun close() {
        try {
            writeToFile("=== DEBUG LOG SESSION ENDED ===")
            writeToFile("Timestamp: ${dateFormatter.format(Date())}")
            logFileWriter?.close()
            logFileWriter = null
            currentLogFile = null
            isInitialized = false
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error closing debug logger")
        }
    }
}