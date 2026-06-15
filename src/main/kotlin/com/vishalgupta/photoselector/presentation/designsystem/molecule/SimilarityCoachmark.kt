package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * The first-run callout for the Similarity lens: a dismissible (NOT modal) card under the toolbar,
 * shown the first time the user picks Similar. It explains what the lens does, that it runs on-device,
 * and roughly how long the cold pass takes — the on-device story being a genuine differentiator we
 * otherwise never state. The user can ignore it and keep culling; "Got it" dismisses it for good.
 */
@Composable
fun SimilarityCoachmark(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = AppTheme.spacing.md, end = AppTheme.spacing.sm)
                .padding(vertical = AppTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(AppTheme.dimens.iconSm),
            )
            Text(
                text = "Finds look-alike shots so you can keep the best and cut the rest. " +
                    "Runs entirely on your device — about a minute the first time.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(text = "Got it", onClick = onDismiss)
        }
    }
}
