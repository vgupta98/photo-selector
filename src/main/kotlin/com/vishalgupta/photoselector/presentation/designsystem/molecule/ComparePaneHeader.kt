package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * The label banner across the top of a compare pane: a [FavouriteStar] when the photo is a keeper,
 * its [fileName] (truncated), and its [positionLabel] (e.g. "3 / 18") pushed to the trailing edge.
 * Rendered in the browser's translucent overlay-chrome colours so it reads as one chrome family
 * floating over the photo. Which pane is *active* is signalled by the pane's border, not here.
 *
 * Caller gives it a width (`fillMaxWidth`) so the trailing position label sits at the edge.
 */
@Composable
fun ComparePaneHeader(
    fileName: String,
    positionLabel: String,
    isFavourite: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppTheme.colors.overlayChromeBackground,
        contentColor = AppTheme.colors.onOverlayChrome,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        ) {
            if (isFavourite) {
                FavouriteStar(
                    filled = true,
                    tint = AppTheme.colors.favourite,
                    modifier = Modifier.size(AppTheme.dimens.iconSm),
                )
            }
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = positionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.onOverlayChrome.copy(alpha = 0.7f),
            )
        }
    }
}
