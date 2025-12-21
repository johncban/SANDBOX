package com.jcb.passbook.presentation.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size class enums - defined locally to avoid dependency issues
 */
enum class WindowWidthSizeClass {
    Compact,    // < 600dp (phones)
    Medium,     // 600-840dp (tablets portrait)
    Expanded    // > 840dp (tablets landscape, desktops)
}

enum class WindowHeightSizeClass {
    Compact,    // < 480dp (landscape phones)
    Medium,     // 480-900dp (normal)
    Expanded    // > 900dp (large screens)
}

/**
 * Determine window size classes based on current screen dimensions.
 * Returns a Pair of (width class, height class).
 */
@Composable
fun rememberWindowSizeClasses(): Pair<WindowWidthSizeClass, WindowHeightSizeClass> {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp

    val widthClass = when {
        widthDp < 600 -> WindowWidthSizeClass.Compact
        widthDp < 840 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }

    val heightClass = when {
        heightDp < 480 -> WindowHeightSizeClass.Compact
        heightDp < 900 -> WindowHeightSizeClass.Medium
        else -> WindowHeightSizeClass.Expanded
    }

    return widthClass to heightClass
}

/**
 * Outer padding for top-level screens based on device size.
 */
@Composable
fun screenPadding(): Dp {
    val (widthClass, _) = rememberWindowSizeClasses()
    return when (widthClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 24.dp
        WindowWidthSizeClass.Expanded -> 32.dp
    }
}

/**
 * Horizontal spacing between columns/panes based on device size.
 */
@Composable
fun horizontalGap(): Dp {
    val (widthClass, _) = rememberWindowSizeClasses()
    return when (widthClass) {
        WindowWidthSizeClass.Compact -> 8.dp
        WindowWidthSizeClass.Medium -> 12.dp
        WindowWidthSizeClass.Expanded -> 16.dp
    }
}

/**
 * Fraction of overall width that content should use.
 * On large devices keep content narrower for readability.
 */
@Composable
fun contentWidthFraction(): Float {
    val (widthClass, _) = rememberWindowSizeClasses()
    return when (widthClass) {
        WindowWidthSizeClass.Compact -> 1.0f
        WindowWidthSizeClass.Medium -> 0.9f
        WindowWidthSizeClass.Expanded -> 0.75f
    }
}
