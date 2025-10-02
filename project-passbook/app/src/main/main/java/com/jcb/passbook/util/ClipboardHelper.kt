package com.jcb.passbook.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle // Correct import for this use case
import android.widget.Toast
import androidx.annotation.RequiresApi

object ClipboardHelper {
    private const val CLIPBOARD_CLEAR_DELAY_MS = 60_000L // 60 seconds

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)

        // For Android 13+, mark the content as sensitive.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // REFACTORED: Use PersistableBundle as required by clip.description.extras
            val extras = PersistableBundle().apply {
                // The key for EXTRA_SENSITIVE is a String, which is valid.
                putBoolean("android.content.ClipDescription.EXTRA_SENSITIVE", true)
            }
            clip.description.extras = extras
        }

        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard.", Toast.LENGTH_SHORT).show()

        // Fallback for older Android versions to clear clipboard manually.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Handler(Looper.getMainLooper()).postDelayed({
                val currentClip = clipboard.primaryClip?.getItemAt(0)?.text
                if (clipboard.hasPrimaryClip() && currentClip?.toString() == text) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, ""))
                }
            }, CLIPBOARD_CLEAR_DELAY_MS)
        }
    }
}
