package com.jcb.passbook.security.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jcb.passbook.BuildConfig
import com.jcb.passbook.data.local.database.entities.AuditEventType
import com.jcb.passbook.data.local.database.entities.AuditOutcome
import com.jcb.passbook.security.audit.AuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MasterKeyManager handles biometric-gated master wrapping keys.
 * The master key is used to wrap/unwrap the Application Master Key (AMK)
 * which in turn protects SQLCipher passphrases and other secrets.
 */
@RequiresApi(Build.VERSION_CODES.M)
@Singleton
class MasterKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogger: AuditLogger,
    private val secureMemoryUtils: SecureMemoryUtils
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val MASTER_WRAP_KEY_ALIAS = "master_wrap_key_v2"
        private const val AMK_STORAGE_KEY = "amk_wrapped_v2"
        private const val AMK_SIZE_BYTES = 32
        private const val AUTH_TIMEOUT_SECONDS = 60
        private const val TAG = "MasterKeyManager"
    }

    /**
     * Initialize master key infrastructure on first run
     */
    suspend fun initializeMasterKey(): Boolean {
        return try {
            if (!hasMasterKey()) {
                generateMasterWrapKey()
                generateAndWrapAMK()
                auditLogger.logSecurityEvent(
                    "Master key infrastructure initialized",
                    "NORMAL",
                    AuditOutcome.SUCCESS
                )
                true
            } else {
                auditLogger.logSecurityEvent(
                    "Master key infrastructure already exists",
                    "NORMAL",
                    AuditOutcome.SUCCESS
                )
                true
            }
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Failed to initialize master key: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            Timber.e(e, "Failed to initialize master key")
            false
        }
    }

    /**
     * Check if master key exists
     */
    private fun hasMasterKey(): Boolean {
        return keyStore.containsAlias(MASTER_WRAP_KEY_ALIAS) &&
                context.getSharedPreferences("master_key_prefs", Context.MODE_PRIVATE)
                    .contains(AMK_STORAGE_KEY)
    }

    /**
     * Generate biometric-gated master wrapping key
     */
    private fun generateMasterWrapKey() {
        val keyGenerator = KeyGenerator.getInstance("AES", "AndroidKeyStore")

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MASTER_WRAP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(!BuildConfig.DEBUG) // Allow debug bypass
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        AUTH_TIMEOUT_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    setUserAuthenticationValidityDurationSeconds(AUTH_TIMEOUT_SECONDS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(true)
                }
                setInvalidatedByBiometricEnrollment(true)
            }
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    /**
     * Generate Application Master Key and wrap it with master key
     */
    private suspend fun generateAndWrapAMK() {
        val amk = ByteArray(AMK_SIZE_BYTES)
        SecureRandom().nextBytes(amk)

        try {
            val wrappedAMK = wrapAMK(amk)
            val prefs = context.getSharedPreferences("master_key_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString(AMK_STORAGE_KEY, Base64.encodeToString(wrappedAMK, Base64.NO_WRAP)).apply()
        } finally {
            secureMemoryUtils.secureWipe(amk)
        }
    }

    /**
     * Wrap AMK with master key
     */
    private fun wrapAMK(amk: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val masterKey = keyStore.getKey(MASTER_WRAP_KEY_ALIAS, null) as SecretKey
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(amk)

        return iv + encrypted // Prepend IV for unwrapping
    }

    /**
     * Unwrap AMK with biometric authentication
     */
    suspend fun unwrapAMK(activity: FragmentActivity): ByteArray? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val biometricManager = BiometricManager.from(context)
                when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock PassBook")
                            .setSubtitle("Authenticate to access your passwords")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            .build()

                        val biometricPrompt = BiometricPrompt(activity,
                            ContextCompat.getMainExecutor(context),
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    try {
                                        val amk = performAMKUnwrap()
                                        if (amk != null) {
                                            auditLogger.logAuthentication(
                                                "SYSTEM",
                                                AuditEventType.LOGIN,
                                                AuditOutcome.SUCCESS
                                            )
                                        }
                                        continuation.resume(amk)
                                    } catch (e: Exception) {
                                        auditLogger.logAuthentication(
                                            "SYSTEM",
                                            AuditEventType.AUTHENTICATION_FAILURE,
                                            AuditOutcome.FAILURE,
                                            e.message
                                        )
                                        continuation.resumeWithException(e)
                                    }
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    auditLogger.logAuthentication(
                                        "SYSTEM",
                                        AuditEventType.AUTHENTICATION_FAILURE,
                                        AuditOutcome.FAILURE,
                                        "Biometric error: $errString"
                                    )
                                    continuation.resume(null)
                                }

                                override fun onAuthenticationFailed() {
                                    auditLogger.logAuthentication(
                                        "SYSTEM",
                                        AuditEventType.AUTHENTICATION_FAILURE,
                                        AuditOutcome.FAILURE,
                                        "Biometric authentication failed"
                                    )
                                    continuation.resume(null)
                                }
                            })

                        biometricPrompt.authenticate(promptInfo)
                    }
                    else -> {
                        auditLogger.logSecurityEvent(
                            "Biometric authentication not available",
                            "WARNING",
                            AuditOutcome.BLOCKED
                        )
                        continuation.resume(null)
                    }
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Perform actual AMK unwrapping after authentication
     */
    private fun performAMKUnwrap(): ByteArray? {
        return try {
            val prefs = context.getSharedPreferences("master_key_prefs", Context.MODE_PRIVATE)
            val wrappedAMKString = prefs.getString(AMK_STORAGE_KEY, null) ?: return null
            val wrappedAMK = Base64.decode(wrappedAMKString, Base64.NO_WRAP)

            if (wrappedAMK.size <= 12) return null

            val iv = wrappedAMK.copyOfRange(0, 12)
            val encrypted = wrappedAMK.copyOfRange(12, wrappedAMK.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val masterKey = keyStore.getKey(MASTER_WRAP_KEY_ALIAS, null) as SecretKey
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))

            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unwrap AMK")
            null
        }
    }

    /**
     * Handle biometric enrollment changes
     */
    suspend fun handleBiometricEnrollmentChange(): Boolean {
        return try {
            auditLogger.logSecurityEvent(
                "Biometric enrollment change detected",
                "ELEVATED",
                AuditOutcome.WARNING
            )

            // Generate new master key
            keyStore.deleteEntry(MASTER_WRAP_KEY_ALIAS)
            generateMasterWrapKey()

            // Re-wrap existing AMK or generate new one
            val prefs = context.getSharedPreferences("master_key_prefs", Context.MODE_PRIVATE)
            if (prefs.contains(AMK_STORAGE_KEY)) {
                // For now, generate new AMK - in production you might want to migrate existing data
                generateAndWrapAMK()
            }

            auditLogger.logUserAction(
                null, "SYSTEM", AuditEventType.KEY_ROTATION,
                "Master key regenerated due to biometric enrollment change",
                "KEYSTORE", MASTER_WRAP_KEY_ALIAS,
                AuditOutcome.SUCCESS, null, "ELEVATED"
            )

            true
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Failed to handle biometric enrollment change: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            false
        }
    }

    /**
     * Force regeneration of master key infrastructure
     */
    suspend fun regenerateMasterKey(): Boolean {
        return try {
            auditLogger.logSecurityEvent(
                "Manual master key regeneration initiated",
                "ELEVATED",
                AuditOutcome.SUCCESS
            )

            // Clean up existing keys
            if (keyStore.containsAlias(MASTER_WRAP_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_WRAP_KEY_ALIAS)
            }

            val prefs = context.getSharedPreferences("master_key_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove(AMK_STORAGE_KEY).apply()

            // Regenerate
            generateMasterWrapKey()
            generateAndWrapAMK()

            auditLogger.logUserAction(
                null, "SYSTEM", AuditEventType.KEY_ROTATION,
                "Master key infrastructure regenerated",
                "KEYSTORE", MASTER_WRAP_KEY_ALIAS,
                AuditOutcome.SUCCESS, null, "ELEVATED"
            )

            true
        } catch (e: Exception) {
            auditLogger.logSecurityEvent(
                "Failed to regenerate master key: ${e.message}",
                "CRITICAL",
                AuditOutcome.FAILURE
            )
            false
        }
    }
}