package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * The "a new version is ready" overlay card. Notify-only: [onDownload] opens the release in the browser;
 * nothing is installed from here. Dismissal comes in two strengths the caller wires to the view model —
 * [onLater] hides it for this session, [onSkip] suppresses this version for good (null when the update is
 * [mandatory], which can't be skipped). [onViewNotes] is null when the feed carried no notes link.
 *
 * Built from the button atoms and theme tokens; it sits in the app's bottom-end overlay stack beside
 * [BackgroundGroupingChip], so it follows the same elevated-surface look.
 */
@Composable
fun UpdateAvailableBanner(
    versionLabel: String,
    mandatory: Boolean,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: (() -> Unit)?,
    onViewNotes: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        // Fixed width, wide enough that the four actions lay out on one line with the weighted spacer
        // keeping slack — so the primary Download button never gets squeezed into wrapping its label.
        modifier = modifier.width(420.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs)) {
                    Text(
                        text = if (mandatory) "Update required" else "Update available",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Rhenium $versionLabel is ready to install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Release notes sits at the leading edge; the spacer pushes the dismiss/download cluster
                // to the trailing edge. The primary action stays last and full-width, so it never wraps.
                onViewNotes?.let {
                    AppTextButton(text = "Release notes", onClick = it, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                onSkip?.let {
                    AppTextButton(text = "Skip", onClick = it, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppTextButton(text = "Later", onClick = onLater)
                AppButton(text = "Download", onClick = onDownload)
            }
        }
    }
}
