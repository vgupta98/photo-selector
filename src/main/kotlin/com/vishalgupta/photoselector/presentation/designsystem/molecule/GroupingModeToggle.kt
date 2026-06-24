package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.common.GroupingMode

/**
 * Grid-toolbar control for the grouping lens: a single-choice segmented row over the three
 * [GroupingMode]s. A segmented button — not three loose chips — because these are mutually
 * exclusive views of the same photos, so the control reads as "pick one" and keeps the visual lens
 * always one tap from being switched off.
 *
 * Each segment carries its glyph *and* its [GroupingMode.label] text — a flat grid for Single, the
 * stacked-frames [Icons.Filled.BurstMode] glyph (shared with the group count badge) for Bursts, and
 * a sparkle for the Similarity lens. The visible words are what make this go-to-market discoverable:
 * a new user can see the three lenses where an icon-only control read as decor.
 *
 * Both the glyph and the label live in the segment's **content** [Row], with the [SegmentedButton]'s
 * own `icon` slot left empty. That slot animates a check in/out on selection; driving it with a
 * custom always-on icon kept it from ever settling, so the test harness's `waitForIdle` spun forever
 * (and the live grid would burn a core redrawing). Leaving the slot empty — exactly the pre-label
 * control's shape — keeps the control idle. Labels stay one line ([softWrap] off) so a narrow toolbar
 * clips rather than wrapping a label onto two rows.
 *
 * Each segment's content is wrapped in a hover [TooltipBox] carrying a one-line "what this lens does"
 * — the desktop pointer makes hover the natural, low-friction explainer. The wrap is INSIDE the
 * [SegmentedButton] (not around it) so the row scope stays intact; an idle (un-hovered) tooltip adds
 * no animation, so the control stays settled for `waitForIdle`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupingModeToggle(
    mode: GroupingMode,
    onSelect: (GroupingMode) -> Unit,
    modifier: Modifier = Modifier,
    // Progress of the background Similarity pass (0..1), or null when none is running. When set, the
    // Similar segment swaps its sparkle for a determinate ring so the lens reads as "still working"
    // whatever lens is currently shown — the on-grid half of the background-pass hint. Determinate (not
    // an indeterminate spinner) on purpose: an infinite animation here would hang the screenshot
    // harness's `waitForIdle`, the same trap the icon-slot note below describes.
    similarityProgress: Float? = null,
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
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(candidate.tooltip) } },
                    state = rememberTooltipState(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ringProgress = similarityProgress?.takeIf { candidate == GroupingMode.Similarity }
                        if (ringProgress != null) {
                            // Same 18.dp footprint as the glyph so the segment's uniform-width measure
                            // doesn't churn when the ring swaps in. The contentDescription keeps the
                            // segment locatable by name (matching the glyph branch below).
                            CircularProgressIndicator(
                                progress = { ringProgress },
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics { contentDescription = candidate.label },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            // contentDescription carries the lens name (matching the visible label) so
                            // the segment is locatable by name for accessibility and tests; the glyph
                            // living in the *content* (not the animating icon slot) keeps the control idle.
                            Icon(
                                imageVector = candidate.icon,
                                contentDescription = candidate.label,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(text = candidate.label, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

/** The hover tooltip copy for each lens — a one-line "what this does", presentation-only. */
private val GroupingMode.tooltip: String
    get() = when (this) {
        GroupingMode.Off -> "Every photo on its own."
        GroupingMode.Time -> "Group rapid-fire frames shot moments apart."
        GroupingMode.Similarity -> "Group look-alike shots using on-device AI."
    }

private val GroupingMode.icon: ImageVector
    get() = when (this) {
        GroupingMode.Off -> Icons.Filled.GridView
        GroupingMode.Time -> Icons.Filled.BurstMode
        GroupingMode.Similarity -> Icons.Filled.AutoAwesome
    }
