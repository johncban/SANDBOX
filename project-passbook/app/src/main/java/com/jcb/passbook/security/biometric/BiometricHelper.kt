package com.jcb.passbook.security.biometric

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    enum class Availability {
        AVAILABLE,
        NOT_AVAILABLE,
        NO_HARDWARE,
        NONE_ENROLLED
    }

    /**
     * Check if biometric authentication is available
     */
    fun checkAvailability(context: Context): Availability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NONE_ENROLLED
            else -> Availability.NOT_AVAILABLE
        }
    }

    /**
     * Build biometric prompt
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun buildPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negative: String,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (Int, CharSequence) -> Unit
    ): BiometricPrompt {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errorCode, errString)
            }
        }
        return BiometricPrompt(activity, activity.mainExecutor, callback)
    }

    /**
     * Build prompt info
     */
    fun buildPromptInfo(
        title: String,
        subtitle: String,
        negative: String
    ): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negative)
            .setConfirmationRequired(false)
            .build()
    }
}