package com.jcb.passbook.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Icon button with enforced 48dp touch target and proper contentDescription.
 */
@Composable
fun AccessibleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        content = { content() }
    )
}

/**
 * Password field with built-in visibility toggle and accessibility descriptions.
 */
@Composable
fun AccessiblePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        supportingText = supportingText,
        trailingIcon = {
            AccessibleIconButton(
                onClick = { passwordVisible = !passwordVisible },
                contentDescription = if (passwordVisible) {
                    "Hide password for $label"
                } else {
                    "Show password for $label"
                }
            ) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = null
                )
            }
        }
    )
}

/**
 * Card that is fully clickable and exposed as a single semantics node.
 */
@Composable
fun AccessibleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            content()
        }
    }
}

/**
 * Simple favorite toggle wrapper. You can integrate this later if you add
 * an explicit “toggle favorite” affordance in the list.
 */
@Composable
fun FavoriteIcon(
    isFavorite: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AccessibleIconButton(
        onClick = {},
        contentDescription = contentDescription,
        modifier = modifier
    ) {
        // Currently just visual; hook into callback if needed
        Icon(
            imageVector = if (isFavorite) {
                androidx.compose.material.icons.Icons.Default.Star
            } else {
                androidx.compose.material.icons.Icons.Default.StarBorder
            },
            contentDescription = null
        )
    }
}
