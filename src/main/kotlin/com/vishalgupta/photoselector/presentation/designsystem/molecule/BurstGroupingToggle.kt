package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Grid-toolbar toggle for burst grouping. Selected (the default) means rapid-fire frames
 * collapse into one tile; cleared means every frame shows as its own tile. A [FilterChip]
 * because that is exactly what this is — a filter over how the grid presents the same photos —
 * and its selected state reads at a glance without a separate label. Uses the same
 * [Icons.Filled.BurstMode] glyph the collapsed-tile badge carries, so the control and the thing
 * it produces share a visual.
 */
@Composable
fun BurstGroupingToggle(
    grouping: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = grouping,
        onClick = onToggle,
        label = { Text("Group bursts") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.BurstMode,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        modifier = modifier,
    )
}
