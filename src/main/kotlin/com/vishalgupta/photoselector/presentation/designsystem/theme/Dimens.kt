package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component-level fixed dimensions shared across screens. Values preserved
 * verbatim from the pre-token UI.
 *
 * Read through `AppTheme.dimens`.
 */
@Immutable
data class Dimens(
    val topBarHeight: Dp = 56.dp,
    val thumbnailMinCell: Dp = 160.dp,
    val focusBorderWidth: Dp = 3.dp,
    val lastViewedIndicatorHeight: Dp = 3.dp,
    val iconSm: Dp = 18.dp,
    val scrollbarThickness: Dp = 8.dp,
    val scrollbarMinHeight: Dp = 48.dp,
    val progressIndicatorLg: Dp = 48.dp,
)

val LocalDimens = staticCompositionLocalOf { Dimens() }
