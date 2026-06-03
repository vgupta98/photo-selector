package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/** One shortcut shown in the [GridKeyboardLegend]: a key cap plus what it does. */
@Immutable
data class KeyHint(val keys: String, val label: String)

/**
 * A slim, always-on footer strip that makes the grid's keyboard model discoverable —
 * the whole point of a keyboard-first cull is undiscoverable if it's invisible. Renders
 * as quiet chrome (matches the top bar's surface) so it informs without competing with the
 * photos. Purely a legend: it surfaces existing shortcuts and handles no input itself.
 *
 * An optional [status] is shown right-aligned (status-bar style) — used to surface a quiet
 * cull-progress tally without crowding the top bar.
 */
@Composable
fun GridKeyboardLegend(
    hints: List<KeyHint>,
    modifier: Modifier = Modifier,
    status: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg),
    ) {
        hints.forEach { hint ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            ) {
                KeyCap(hint.keys)
                Text(
                    text = hint.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyCap(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.sm, vertical = 2.dp),
        )
    }
}
