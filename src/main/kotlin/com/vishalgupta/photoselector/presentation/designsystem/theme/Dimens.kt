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
    // The adaptive grid's minimum tile width. Tuned down from 160 toward a contact-sheet
    // density: more frames per row for a culler scanning fast, still large enough to judge a
    // shot at a glance. The grid's gutters and content padding are tightened to match.
    val thumbnailMinCell: Dp = 132.dp,
    val focusBorderWidth: Dp = 3.dp,
    val lastViewedIndicatorHeight: Dp = 3.dp,
    val iconSm: Dp = 18.dp,
    val iconLg: Dp = 48.dp,
    // Bottom inset for the browser's confirmation toast. Lifts it clear of the bottom-center
    // HUD + keyboard-legend stack so a membership toast and the revealed HUD don't collide.
    val browserToastBottomInset: Dp = 128.dp,
    val scrollbarThickness: Dp = 8.dp,
    val scrollbarMinHeight: Dp = 48.dp,
    val progressIndicatorLg: Dp = 48.dp,
    // Thickness of the little drag-style handle that closes an expanded burst (one of the 3dp
    // hairline family alongside focusBorderWidth / lastViewedIndicatorHeight).
    val burstHandleHeight: Dp = 3.dp,
    // Horizontal inset inside the small overlay badges (burst count, category chip). Tighter than
    // the spacing scale by design, shared so both badges stay visually identical.
    val badgeInset: Dp = 5.dp,
)

val LocalDimens = staticCompositionLocalOf { Dimens() }
