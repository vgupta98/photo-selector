package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * The full-width banner that heads an expanded burst in the grid: a stacked-frames glyph, a label
 * naming the run (count + that the frames were shot together, which answers "why are these
 * grouped?"), and a Collapse action that folds them back into one tile. Spans the whole grid row so
 * the frames beneath it read as a single section.
 */
@Composable
fun BurstExpandedHeader(
    frameCount: Int,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.tileBackground)
            .padding(horizontal = AppTheme.spacing.sm, vertical = AppTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.BurstMode,
            contentDescription = null,
            modifier = Modifier.size(AppTheme.dimens.iconSm),
        )
        Text(
            text = "$frameCount frames shot in a burst",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.weight(1f))
        AppTextButton(
            text = "Collapse",
            onClick = onCollapse,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
