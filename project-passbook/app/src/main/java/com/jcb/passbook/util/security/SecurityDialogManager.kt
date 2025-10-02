package com.jcb.passbook.util.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.jcb.passbook.room.AuditEventType
import com.jcb.passbook.room.AuditOutcome
import com.jcb.passbook.util.audit.AuditLogger
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages security-related dialogs with user interaction options and audit logging.
 * Provides configurable security responses based on threat severity.
 */
@Singleton
class SecurityDialogManager @Inject constructor(
    private val auditLogger: AuditLogger
) {
    companion object {
        private const val TAG = "SecurityDialogManager"
        
        // HARDCODED SECURITY BEHAVIOR SETTINGS
        private const val AUTO_DISMISS_TIMEOUT_MS = 30000L // 30 seconds
        private const val REQUIRE_CONFIRMATION_FOR_OVERRIDE = true
        private const val LOG_USER_INTERACTIONS = true
        private const val SHOW_TECHNICAL_DETAILS = true
    }

    data class SecurityDialogState(
        val isVisible: Boolean = false,
        val title: String = "",
        val message: String = "",
        val severity: EnhancedRootDetector.SecurityLevel = EnhancedRootDetector.SecurityLevel.LOW,
        val allowOverride: Boolean = false,
        val detectionMethods: List<String> = emptyList(),
        val onDismiss: (() -> Unit)? = null,
        val onConfirm: (() -> Unit)? = null,
        val onOverride: (() -> Unit)? = null
    )

    private var _dialogState = mutableStateOf(SecurityDialogState())
    val dialogState: State<SecurityDialogState> = _dialogState

    /**
     * Show security dialog based on root detection results
     */
    suspend fun showRootDetectionDialog(
        result: EnhancedRootDetector.RootDetectionResult,
        onDismiss: () -> Unit,
        onOverride: (() -> Unit)? = null
    ) {
        if (LOG_USER_INTERACTIONS) {
            auditLogger.logUserAction(
                userId = null,
                username = "USER",
                eventType = AuditEventType.SECURITY_EVENT,
                action = "Security dialog displayed",
                resourceType = "SECURITY_DIALOG",
                resourceId = "ROOT_DETECTION",
                outcome = AuditOutcome.SUCCESS,
                securityLevel = when (result.severity) {
                    EnhancedRootDetector.SecurityLevel.LOW -> "INFO"
                    EnhancedRootDetector.SecurityLevel.MEDIUM -> "WARNING"
                    EnhancedRootDetector.SecurityLevel.HIGH -> "ELEVATED"
                    EnhancedRootDetector.SecurityLevel.CRITICAL -> "CRITICAL"
                }
            )
        }

        val title = when (result.severity) {
            EnhancedRootDetector.SecurityLevel.LOW -> "Security Notice"
            EnhancedRootDetector.SecurityLevel.MEDIUM -> "Security Warning"
            EnhancedRootDetector.SecurityLevel.HIGH -> "Security Alert"
            EnhancedRootDetector.SecurityLevel.CRITICAL -> "Critical Security Threat"
        }

        _dialogState.value = SecurityDialogState(
            isVisible = true,
            title = title,
            message = result.userMessage,
            severity = result.severity,
            allowOverride = result.allowUserOverride,
            detectionMethods = result.detectionMethods,
            onDismiss = {
                handleDialogDismiss("dismiss", result.severity)
                onDismiss()
            },
            onConfirm = {
                handleDialogDismiss("confirm_exit", result.severity)
                onDismiss()
            },
            onOverride = onOverride?.let { override ->
                {
                    handleDialogDismiss("user_override", result.severity)
                    override()
                }
            }
        )

        // Auto-dismiss for critical threats (no user choice)
        if (result.severity == EnhancedRootDetector.SecurityLevel.CRITICAL) {
            delay(AUTO_DISMISS_TIMEOUT_MS)
            if (_dialogState.value.isVisible) {
                handleDialogDismiss("auto_timeout", result.severity)
                onDismiss()
            }
        }
    }

    /**
     * Hide the security dialog
     */
    fun hideDialog() {
        _dialogState.value = _dialogState.value.copy(isVisible = false)
    }

    /**
     * Handle dialog dismissal with audit logging
     */
    private suspend fun handleDialogDismiss(action: String, severity: EnhancedRootDetector.SecurityLevel) {
        if (LOG_USER_INTERACTIONS) {
            auditLogger.logUserAction(
                userId = null,
                username = "USER",
                eventType = AuditEventType.SECURITY_EVENT,
                action = "Security dialog action: $action",
                resourceType = "SECURITY_DIALOG",
                resourceId = "ROOT_DETECTION_RESPONSE",
                outcome = when (action) {
                    "user_override" -> AuditOutcome.WARNING
                    "confirm_exit" -> AuditOutcome.SUCCESS
                    "auto_timeout" -> AuditOutcome.BLOCKED
                    else -> AuditOutcome.SUCCESS
                },
                securityLevel = when (severity) {
                    EnhancedRootDetector.SecurityLevel.LOW -> "INFO"
                    EnhancedRootDetector.SecurityLevel.MEDIUM -> "WARNING"
                    EnhancedRootDetector.SecurityLevel.HIGH -> "ELEVATED"
                    EnhancedRootDetector.SecurityLevel.CRITICAL -> "CRITICAL"
                }
            )
        }

        Timber.i("$TAG: Security dialog dismissed with action: $action, severity: $severity")
        hideDialog()
    }
}

/**
 * Composable function for displaying security dialogs
 */
@Composable
fun SecurityDialog(
    state: SecurityDialogManager.SecurityDialogState,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return

    AlertDialog(
        onDismissRequest = {
            if (state.severity != EnhancedRootDetector.SecurityLevel.CRITICAL) {
                state.onDismiss?.invoke()
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (state.severity) {
                        EnhancedRootDetector.SecurityLevel.LOW,
                        EnhancedRootDetector.SecurityLevel.MEDIUM -> Icons.Default.Security
                        EnhancedRootDetector.SecurityLevel.HIGH,
                        EnhancedRootDetector.SecurityLevel.CRITICAL -> Icons.Default.Warning
                    },
                    contentDescription = "Security Icon",
                    tint = when (state.severity) {
                        EnhancedRootDetector.SecurityLevel.LOW -> Color.Blue
                        EnhancedRootDetector.SecurityLevel.MEDIUM -> Color(0xFFFF9800) // Orange
                        EnhancedRootDetector.SecurityLevel.HIGH -> Color(0xFFFF5722) // Deep Orange
                        EnhancedRootDetector.SecurityLevel.CRITICAL -> Color.Red
                    }
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Main message
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )

                // Technical details (if enabled and available)
                if (SecurityDialogManager.SHOW_TECHNICAL_DETAILS && state.detectionMethods.isNotEmpty()) {
                    Divider()
                    Text(
                        text = "Technical Details:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    state.detectionMethods.take(5).forEach { method ->
                        Text(
                            text = "• $method",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (state.detectionMethods.size > 5) {
                        Text(
                            text = "... and ${state.detectionMethods.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Severity indicator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (state.severity) {
                            EnhancedRootDetector.SecurityLevel.LOW -> Color.Blue.copy(alpha = 0.1f)
                            EnhancedRootDetector.SecurityLevel.MEDIUM -> Color(0xFFFF9800).copy(alpha = 0.1f)
                            EnhancedRootDetector.SecurityLevel.HIGH -> Color(0xFFFF5722).copy(alpha = 0.1f)
                            EnhancedRootDetector.SecurityLevel.CRITICAL -> Color.Red.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Threat Level: ${state.severity.name}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (state.severity) {
                                EnhancedRootDetector.SecurityLevel.LOW -> "Low risk - proceed with caution"
                                EnhancedRootDetector.SecurityLevel.MEDIUM -> "Medium risk - review required"
                                EnhancedRootDetector.SecurityLevel.HIGH -> "High risk - immediate attention needed"
                                EnhancedRootDetector.SecurityLevel.CRITICAL -> "Critical risk - automatic protection engaged"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Override button (if allowed)
                if (state.allowOverride && state.onOverride != null) {
                    OutlinedButton(
                        onClick = {
                            if (SecurityDialogManager.REQUIRE_CONFIRMATION_FOR_OVERRIDE) {
                                // Show confirmation dialog for override
                                state.onOverride.invoke()
                            } else {
                                state.onOverride.invoke()
                            }
                        }
                    ) {
                        Text("Continue Anyway")
                    }
                }

                // Primary action button
                Button(
                    onClick = {
                        state.onConfirm?.invoke() ?: state.onDismiss?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (state.severity) {
                            EnhancedRootDetector.SecurityLevel.CRITICAL -> Color.Red
                            EnhancedRootDetector.SecurityLevel.HIGH -> Color(0xFFFF5722)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        when (state.severity) {
                            EnhancedRootDetector.SecurityLevel.CRITICAL -> "Exit App"
                            EnhancedRootDetector.SecurityLevel.HIGH -> "Exit for Security"
                            else -> "OK"
                        }
                    )
                }
            }
        },
        dismissButton = {
            // Only show dismiss for non-critical threats
            if (state.severity != EnhancedRootDetector.SecurityLevel.CRITICAL) {
                TextButton(
                    onClick = { state.onDismiss?.invoke() }
                ) {
                    Text("Cancel")
                }
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state.severity != EnhancedRootDetector.SecurityLevel.CRITICAL,
            dismissOnClickOutside = state.severity != EnhancedRootDetector.SecurityLevel.CRITICAL
        ),
        modifier = modifier
    )

    // Auto-dismiss countdown for critical threats
    if (state.severity == EnhancedRootDetector.SecurityLevel.CRITICAL) {
        var countdown by remember { mutableStateOf(30) }
        
        LaunchedEffect(state.isVisible) {
            if (state.isVisible) {
                while (countdown > 0) {
                    delay(1000)
                    countdown--
                }
                state.onConfirm?.invoke() ?: state.onDismiss?.invoke()
            }
        }

        // Show countdown
        LaunchedEffect(countdown) {
            if (countdown <= 0) {
                state.onConfirm?.invoke() ?: state.onDismiss?.invoke()
            }
        }
    }
}