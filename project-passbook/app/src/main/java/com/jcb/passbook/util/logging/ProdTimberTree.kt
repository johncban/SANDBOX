package com.jcb.passbook.util.logging

import android.util.Log
//import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class ProdTimberTree : Timber.Tree() {
    //private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return  // Only WARN and ERROR

        val sanitizedMessage = sanitizeMessage(message)

        // Log to logcat (optional)
        Log.println(priority, tag ?: "PassBook", sanitizedMessage)

        // Report to Crashlytics
        /***
        if (t != null) {
            // Log exception with sanitized message as reason
            crashlytics.recordException(
                Throwable("$sanitizedMessage\nCaused by: ${t::class.java.simpleName}: ${t.message}", t)
            )
        } else {
            crashlytics.log(sanitizedMessage)
        }
        ***/
    }

    /**
     * Sanitize message to exclude any sensitive info, e.g. passwords, keys, personal data.
     * Implement app-specific rules here.
     */
    private fun sanitizeMessage(message: String): String {
        // Example: remove password patterns or replace with placeholders
        // This must be adapted specifically for your app log patterns
        return message.replace(Regex("(?i)password=\\S+"), "password=****")
            .replace(Regex("(?i)passphrase=\\S+"), "passphrase=****")
            // Expand with more rules as needed
            .trim()
    }
}
