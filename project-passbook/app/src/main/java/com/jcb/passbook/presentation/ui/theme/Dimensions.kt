package com.jcb.passbook.presentation.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive dimension values based on device screen size
 * Ensures consistent, accessible spacing across all devices
 */
data class ResponsiveDimensions(
    // Padding values
    val paddingExtraSmall: Dp = 4.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 16.dp,
    val paddingLarge: Dp = 24.dp,
    val paddingExtraLarge: Dp = 32.dp,

    // Element sizes
    val touchTargetSize: Dp = 48.dp,
    val smallButtonHeight: Dp = 40.dp,
    val standardButtonHeight: Dp = 48.dp,
    val largeButtonHeight: Dp = 56.dp,

    // Icon sizes
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 48.dp,
    val iconExtraLarge: Dp = 64.dp,

    // Spacing
    val spacingXS: Dp = 4.dp,
    val spacingS: Dp = 8.dp,
    val spacingM: Dp = 12.dp,
    val spacingL: Dp = 16.dp,
    val spacingXL: Dp = 24.dp,
    val spacingXXL: Dp = 32.dp,

    // TextField
    val textFieldHeight: Dp = 56.dp
)

/**
 * Get responsive dimensions based on window size class
 * Automatically adjusts padding, sizes, etc. for different devices
 */
@Composable
fun getResponsiveDimensions(windowSizeClass: WindowWidthSizeClass): ResponsiveDimensions {
    return when (windowSizeClass) {
        WindowWidthSizeClass.Compact -> ResponsiveDimensions(
            paddingMedium = 12.dp,
            paddingLarge = 16.dp,
            iconExtraLarge = 56.dp,
            textFieldHeight = 48.dp
        )
        WindowWidthSizeClass.Medium -> ResponsiveDimensions(
            paddingMedium = 16.dp,
            paddingLarge = 24.dp,
            iconExtraLarge = 64.dp,
            textFieldHeight = 56.dp
        )
        WindowWidthSizeClass.Expanded -> ResponsiveDimensions(
            paddingMedium = 24.dp,
            paddingLarge = 32.dp,
            paddingExtraLarge = 48.dp,
            touchTargetSize = 56.dp,
            iconExtraLarge = 72.dp,
            textFieldHeight = 60.dp
        )
        else -> ResponsiveDimensions() // Default fallback
    }
}
