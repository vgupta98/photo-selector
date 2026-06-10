package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.vishalgupta.photoselector.presentation.common.GroupingMode

/**
 * Grid-toolbar control for the grouping lens: a single-choice segmented row over the three
 * [GroupingMode]s. A segmented button — not three loose chips — because these are mutually
 * exclusive views of the same photos, so the control reads as "pick one" and keeps the visual lens
 * always one tap from being switched off.
 *
 * Icon-only to stay compact in a busy toolbar (the label rides as the accessibility description):
 * a flat grid for Off, the stacked-frames [Icons.Filled.BurstMode] glyph (shared with the burst
 * count badge) for Time, and a sparkle for the Similarity lens.
 */
@Composable
fun GroupingModeToggle(
    mode: GroupingMode,
    onSelect: (GroupingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = GroupingMode.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        modes.forEachIndexed { index, candidate ->
            SegmentedButton(
                selected = candidate == mode,
                onClick = { onSelect(candidate) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                icon = {},
            ) {
                Icon(imageVector = candidate.icon, contentDescription = candidate.label)
            }
        }
    }
}

private val GroupingMode.icon: ImageVector
    get() = when (this) {
        GroupingMode.Off -> Icons.Filled.GridView
        GroupingMode.Time -> Icons.Filled.BurstMode
        GroupingMode.Similarity -> Icons.Filled.AutoAwesome
    }
