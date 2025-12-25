package com.jcb.passbook.utils.logging

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.util.regex.Pattern

/**
 * RestrictiveReleaseTree filters out sensitive information from logs in release builds.
 * Blocks potentially sensitive data like passwords, keys, tokens, and personal information.
 */
class RestrictiveReleaseTree : Timber.Tree() {

    companion object {
        // Patterns to detect sensitive information
        private val SENSITIVE_PATTERNS = listOf(
            Pattern.compile("(?i)password[\"'\\s:=]*[\"']?([^\"'\\s,}\\]]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)key[\"'\\s:=]*[\"']?([^\"'\\s,}\\]]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)token[\"'\\s:=]*[\"']?([^\"'\\s,}\\]]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)secret[\"'\\s:=]*[\"']?([^\"'\\s,}\\]]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)passphrase[\"'\\s:=]*[\"']?([^\"'\\s,}\\]]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)pin[\"'\\s:=]*[\"']?([^\"'\\s,}\\]]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b[0-9a-fA-F]{32,}\\b"), // Hex strings that might be keys/hashes
            Pattern.compile("\\b[A-Za-z0-9+/]{20,}={0,2}\\b"), // Base64 encoded data
        )

        // Keywords that indicate sensitive context
        private val SENSITIVE_KEYWORDS = setOf(
            "amk", "esk", "masterkey", "sessionkey", "cipher", "encrypt", "decrypt",
            "biometric", "keystore", "passphrase", "credential", "auth"
        )

        private const val REDACTED_PLACEHOLDER = "[REDACTED]"
        private const val MAX_LOG_LENGTH = 4000 // Android Log limit
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log ERROR and WARN in release builds
        if (priority < Log.WARN) {
            return
        }

        // Filter and sanitize the message
        val sanitizedMessage = sanitizeMessage(message)

        // Truncate if too long
        val finalMessage = if (sanitizedMessage.length > MAX_LOG_LENGTH) {
            sanitizedMessage.substring(0, MAX_LOG_LENGTH - 20) + "... [TRUNCATED]"
        } else {
            sanitizedMessage
        }

        // Log the sanitized message
        Log.println(priority, tag ?: "PassBook", finalMessage)

        // Log throwable if present (but sanitize its message too)
        t?.let { throwable ->
            val sanitizedThrowable = sanitizeThrowable(throwable)
            Log.println(priority, tag ?: "PassBook", Log.getStackTraceString(sanitizedThrowable))
        }
    }

    /**
     * Sanitize log message by removing sensitive information
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun sanitizeMessage(message: String): String {
        var sanitized = message

        // Check if message contains sensitive keywords
        val lowerMessage = message.lowercase()
        val containsSensitiveKeyword = SENSITIVE_KEYWORDS.any { keyword ->
            lowerMessage.contains(keyword)
        }

        if (containsSensitiveKeyword) {
            // Apply pattern-based redaction
            SENSITIVE_PATTERNS.forEach { pattern ->
                sanitized = pattern.matcher(sanitized).replaceAll { matchResult ->
                    val prefix = matchResult.group(0).substring(0, matchResult.start(1) - matchResult.start())
                    "$prefix$REDACTED_PLACEHOLDER"
                }
            }
        }

        return sanitized
    }

    /**
     * Sanitize throwable messages
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        val originalMessage = throwable.message
        if (originalMessage != null) {
            val sanitizedMessage = sanitizeMessage(originalMessage)
            if (sanitizedMessage != originalMessage) {
                // Create new exception with sanitized message but preserve stack trace
                val sanitizedException = Exception(sanitizedMessage, throwable.cause)
                sanitizedException.stackTrace = throwable.stackTrace
                return sanitizedException
            }
        }
        return throwable
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Only allow WARN and ERROR in release
        return priority >= Log.WARN
    }
}