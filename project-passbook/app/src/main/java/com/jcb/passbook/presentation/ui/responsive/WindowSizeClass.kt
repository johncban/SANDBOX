package com.jcb.passbook.presentation.ui.responsive

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Screen size category for responsive design
 */
enum class ScreenSize {
    COMPACT,    // < 600dp (phones)
    MEDIUM,     // 600-840dp (tablets portrait, landscape phones)
    EXPANDED    // > 840dp (large tablets)
}

/**
 * Device orientation
 */
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Complete screen information for responsive design
 */
data class ScreenInfo(
    val size: ScreenSize,
    val orientation: ScreenOrientation,
    val width: Int,
    val height: Int,
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass
)

/**
 * Remember and compute current screen information
 */
@Composable
fun rememberScreenInfo(): ScreenInfo {
    val configuration = LocalConfiguration.current

    return remember(configuration) {
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp

        val size = when {
            widthDp < 600 -> ScreenSize.COMPACT
            widthDp < 840 -> ScreenSize.MEDIUM
            else -> ScreenSize.EXPANDED
        }

        val orientation = if (widthDp > heightDp) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        }

        val widthSizeClass = when {
            widthDp < 600 -> WindowWidthSizeClass.Compact
            widthDp < 840 -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        }

        val heightSizeClass = when {
            heightDp < 480 -> WindowHeightSizeClass.Compact
            heightDp < 900 -> WindowHeightSizeClass.Medium
            else -> WindowHeightSizeClass.Expanded
        }

        ScreenInfo(
            size = size,
            orientation = orientation,
            width = widthDp,
            height = heightDp,
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass
        )
    }
}

/**
 * Check if device is in tablet mode
 */
@Composable
fun isTabletMode(): Boolean {
    val screenInfo = rememberScreenInfo()
    return screenInfo.size == ScreenSize.MEDIUM || screenInfo.size == ScreenSize.EXPANDED
}

/**
 * Check if device is in landscape orientation
 */
@Composable
fun isLandscape(): Boolean {
    val screenInfo = rememberScreenInfo()
    return screenInfo.orientation == ScreenOrientation.LANDSCAPE
}

/**
 * Get recommended column count for list layouts
 */
@Composable
fun getRecommendedColumnCount(): Int {
    val screenInfo = rememberScreenInfo()
    return when (screenInfo.size) {
        ScreenSize.COMPACT -> 1
        ScreenSize.MEDIUM -> if (screenInfo.orientation == ScreenOrientation.LANDSCAPE) 2 else 1
        ScreenSize.EXPANDED -> if (screenInfo.width > 1200) 3 else 2
    }
}
