package com.jcb.passbook.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class PasswordStrength(val label: String, val percentage: Float) {
    WEAK("Weak", 0.25f),
    MEDIUM("Medium", 0.5f),
    STRONG("Strong", 0.75f),
    VERY_STRONG("Very Strong", 1.0f);

    val color: Color
        @Composable get() = when (this) {
            WEAK -> Color(0xFFE74C3C)
            MEDIUM -> Color(0xFFF39C12)
            STRONG -> Color(0xFF3498DB)
            VERY_STRONG -> Color(0xFF27AE60)
        }
}

fun calculatePasswordStrength(password: String): PasswordStrength {
    var score = 0
    if (password.length >= 12) score += 2 else if (password.length >= 8) score += 1
    if (password.any { it.isUpperCase() }) score += 1
    if (password.any { it.isLowerCase() }) score += 1
    if (password.any { it.isDigit() }) score += 1
    if (password.any { !it.isLetterOrDigit() }) score += 2

    return when {
        score <= 2 -> PasswordStrength.WEAK
        score <= 4 -> PasswordStrength.MEDIUM
        score <= 6 -> PasswordStrength.STRONG
        else -> PasswordStrength.VERY_STRONG
    }
}

@Composable
fun PasswordStrengthIndicator(password: String, modifier: Modifier = Modifier) {
    val strength = calculatePasswordStrength(password)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Strength bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(strength.percentage)
                    .background(strength.color)
            )
        }

        // Strength label
        Text(
            text = "Password strength: ${strength.label}",
            style = MaterialTheme.typography.labelSmall,
            color = strength.color
        )
    }
}
