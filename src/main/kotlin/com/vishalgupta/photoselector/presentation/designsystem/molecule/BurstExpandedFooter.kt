package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

// Fraction of the row the centred handle spans - a layout ratio, not a theme dimension, so it lives
// here as a named constant rather than in Dimens.
private const val HANDLE_WIDTH_FRACTION = 0.12f

/**
 * Closes an expanded burst: a full-width bar mirroring [BurstExpandedHeader], with a short centred
 * handle, so the unfolded run is bracketed top and bottom and clearly ends before the rest of the
 * grid resumes. Being full-width, it also forces the following tiles onto a fresh row.
 */
@Composable
fun BurstExpandedFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.tileBackground)
            .padding(vertical = AppTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(HANDLE_WIDTH_FRACTION)
                .height(AppTheme.dimens.burstHandleHeight)
                .clip(CircleShape)
                .background(AppTheme.colors.burstFrameRing),
        )
    }
}
