package com.jcb.passbook.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MasterDetailLayout(
    modifier: Modifier = Modifier,
    masterContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    masterWeight: Float = 0.35f
) {
    Row(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(masterWeight)
                .fillMaxHeight()
        ) {
            masterContent()
        }

        Box(
            modifier = Modifier
                .weight(1f - masterWeight)
                .fillMaxHeight()
        ) {
            detailContent()
        }
    }
}

@Composable
fun ResponsiveScaffold(
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val horizontalPadding = when (windowWidthSizeClass) {
        WindowWidthSizeClass.Compact -> 0
        WindowWidthSizeClass.Medium -> 32
        WindowWidthSizeClass.Expanded -> 64
        else -> 0
    }

    Scaffold(modifier = modifier) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding.dp)
        ) {
            content(paddingValues)
        }
    }
}
