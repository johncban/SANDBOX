package com.jcb.passbook.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Accessible password field with visibility toggle and screen reader support
 */
@Composable
fun AccessiblePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    maxCharacters: Int = 128,
    contentDescription: String = "Password field",
    onFocusedChange: (Boolean) -> Unit = {}
) {
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.length <= maxCharacters) {
                    onValueChange(newValue)
                }
            },
            label = { Text(label) },
            modifier = Modifier
                .semantics {
                    this.contentDescription = contentDescription
                },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { isPasswordVisible = !isPasswordVisible },
                    modifier = Modifier.semantics {
                        this.contentDescription = if (isPasswordVisible) {
                            "Hide password"
                        } else {
                            "Show password"
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = null
                    )
                }
            },
            isError = isError,
            enabled = enabled
        )

        // Character count
        Text(
            text = "${value.length}/$maxCharacters characters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                this.contentDescription = "${value.length} of $maxCharacters characters entered"
            }
        )

        supportingText?.invoke()
    }
}

/**
 * Accessible text field with input validation feedback
 */
@Composable
fun AccessibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    supportingText: @Composable (() -> Unit)? = null,
    maxCharacters: Int = 256,
    contentDescription: String = label
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.length <= maxCharacters) {
                    onValueChange(newValue)
                }
            },
            label = { Text(label) },
            modifier = Modifier
                .semantics {
                    this.contentDescription = if (isError && errorMessage != null) {
                        "$contentDescription - Error: $errorMessage"
                    } else {
                        contentDescription
                    }
                },
            isError = isError,
            enabled = enabled,
            singleLine = false
        )

        if (isError && errorMessage != null) {
            Text(
                text = "Error: $errorMessage",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = "${value.length}/$maxCharacters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        supportingText?.invoke()
    }
}

/**
 * Accessible card with semantic click handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription?.let {
                this.contentDescription = it
            }
        },
        enabled = enabled
    ) {
        content()
    }
}

/**
 * Accessible icon button with mandatory content description
 */
@Composable
fun AccessibleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
        enabled = enabled
    ) {
        content()
    }
}

/**
 * Accessible button with enhanced semantics
 */
@Composable
fun AccessibleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: label
            if (isLoading) {
                this.contentDescription = "$label - Loading"
            }
            if (!enabled) {
                this.contentDescription = "$label - Disabled"
            }
        },
        enabled = enabled && !isLoading
    ) {
        content()
    }
}

/**
 * Accessible icon with mandatory content description
 */
@Composable
fun AccessibleIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
        tint = tint
    )
}
