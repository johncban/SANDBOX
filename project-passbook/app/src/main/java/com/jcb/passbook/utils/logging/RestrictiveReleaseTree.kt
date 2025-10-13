package com.jcb.passbook.utils.logging

import android.util.Log
import timber.log.Timber

/**
 * Only logs non-sensitive error messages in production, and never user data.
 * Optionally, forwards crash reports to Analytics/Crashlytics but never logs message content.
 */
class RestrictiveReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only warn and error logs get attention in prod.
        if (priority >= Log.WARN) {
            // Replace with Crashlytics/Sentry/CrashReportingLib logic
            // Example:
            // Crashlytics.log(message)
            // Crashlytics.logException(t)
            // (but never send full message if it could contain user data)
        }
        // Do not print logs to Logcat, and NEVER log PII or secret data!
    }
}