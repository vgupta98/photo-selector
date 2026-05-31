package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * Shared top-bar chrome: a fixed-height, full-width, [containerColor]-backed row
 * with standard horizontal padding, vertical centering, and inter-item gap. The
 * [content] slot fills the row — callers place a `Modifier.weight(1f)` spacer (or
 * a weighted child) where the gap between leading and trailing items should go.
 */
@Composable
fun TopBarScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(AppTheme.dimens.topBarHeight)
            .background(containerColor)
            .padding(horizontal = AppTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        content = content,
    )
}
