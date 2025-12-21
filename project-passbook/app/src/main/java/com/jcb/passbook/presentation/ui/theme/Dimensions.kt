package com.jcb.passbook.presentation.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive dimension system for adaptive layouts
 * Automatically scales based on device screen width
 */
data class ResponsiveDimensions(
    // Padding dimensions
    val paddingXSmall: Dp = 4.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 16.dp,
    val paddingLarge: Dp = 24.dp,
    val paddingXLarge: Dp = 32.dp,

    // Spacing dimensions
    val spacingSmall: Dp = 8.dp,
    val spacingMedium: Dp = 16.dp,
    val spacingLarge: Dp = 24.dp,
    val spacingXLarge: Dp = 32.dp,

    // Component dimensions
    val buttonHeight: Dp = 48.dp,
    val textFieldHeight: Dp = 56.dp,
    val iconSize: Dp = 24.dp,
    val largeIconSize: Dp = 64.dp,

    // Layout configuration
    val maxContentWidth: Dp = Dp.Unspecified,
    val gridColumns: Int = 1,
    val itemSpacing: Dp = 8.dp
)

/**
 * Create responsive dimensions based on window width class
 * Supports phone, tablet, and desktop layouts
 *
 * @param windowWidthClass The current window width size class
 * @return ResponsiveDimensions configured for the screen size
 */
@Composable
fun createResponsiveDimensions(
    windowWidthClass: WindowWidthSizeClass
): ResponsiveDimensions {
    return when (windowWidthClass) {
        WindowWidthSizeClass.Compact -> {
            // Phone: < 600dp
            ResponsiveDimensions(
                paddingLarge = 16.dp,
                gridColumns = 1
            )
        }
        WindowWidthSizeClass.Medium -> {
            // Tablet: 600-840dp
            ResponsiveDimensions(
                paddingLarge = 24.dp,
                gridColumns = 2,
                maxContentWidth = 800.dp,
                itemSpacing = 12.dp
            )
        }
        WindowWidthSizeClass.Expanded -> {
            // Desktop: > 840dp
            ResponsiveDimensions(
                paddingLarge = 32.dp,
                gridColumns = 3,
                maxContentWidth = 1200.dp,
                itemSpacing = 16.dp
            )
        }
        else -> {
            // Fallback for any unknown size class (future-proofing)
            ResponsiveDimensions(
                paddingLarge = 16.dp,
                gridColumns = 1
            )
        }
    }
}

/**
 * Convenience object for quick access to default dimensions
 */
object ResponsiveDefaults {
    val phoneDimensions = ResponsiveDimensions()
    val tabletDimensions = ResponsiveDimensions(
        paddingLarge = 24.dp,
        gridColumns = 2,
        maxContentWidth = 800.dp
    )
    val desktopDimensions = ResponsiveDimensions(
        paddingLarge = 32.dp,
        gridColumns = 3,
        maxContentWidth = 1200.dp
    )
}
