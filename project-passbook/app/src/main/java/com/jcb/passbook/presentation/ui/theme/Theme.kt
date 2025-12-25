package com.jcb.passbook.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Light color scheme using your Teal / security palette
private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealLight,
    onPrimaryContainer = TealDark,

    secondary = SuccessGreen,
    onSecondary = Color.White,
    secondaryContainer = SuccessGreen.copy(alpha = 0.15f),
    onSecondaryContainer = TealDark,

    tertiary = WarningOrange,
    onTertiary = Color.White,
    tertiaryContainer = WarningOrange.copy(alpha = 0.15f),
    onTertiaryContainer = Color(0xFF4D3003),

    error = ErrorRed,
    onError = Color.White,

    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = TextSecondary
)

// Dark color scheme using your Teal / security palette
private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = TealLight,

    secondary = SuccessGreen,
    onSecondary = Color.Black,
    secondaryContainer = SuccessGreen.copy(alpha = 0.25f),
    onSecondaryContainer = Color(0xFFB2DFDB),

    tertiary = WarningOrange,
    onTertiary = Color.Black,
    tertiaryContainer = WarningOrange.copy(alpha = 0.25f),
    onTertiaryContainer = Color(0xFFFFE0B2),

    error = ErrorRed,
    onError = Color.White,

    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = TextSecondaryDark
)

@Composable
fun PassbookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
