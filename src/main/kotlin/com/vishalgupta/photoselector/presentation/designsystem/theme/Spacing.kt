package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Use these for padding and arrangement gaps instead of
 * literal dp values so spacing stays consistent across screens.
 *
 * Read through `AppTheme.spacing`.
 */
@Immutable
data class Spacing(
    // Tightest gap on the scale, for badge interiors where xs (4dp) reads too loose.
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
