package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * An inline busy row: a horizontal progress bar with a trailing [label]. Used while a long-running
 * operation (scan, copy, grouping) is in flight. Pass [progress] in `0f..1f` for a determinate bar
 * that fills as the work advances; leave it null for an indeterminate sweep when there's no count.
 */
@Composable
fun BusyBar(label: String, modifier: Modifier = Modifier, progress: Float? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
    ) {
        if (progress == null) {
            LinearProgressIndicator(Modifier.weight(1f))
        } else {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
        }
        Text(label)
    }
}
