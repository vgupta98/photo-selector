package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList

/** One shortcut shown in a [KeyboardLegend]: a key cap plus what it does. */
@Immutable
data class KeyHint(val keys: String, val label: String)

/**
 * A slim, always-on strip that makes a keyboard model discoverable — a keyboard-first cull is
 * undiscoverable if the shortcuts are invisible. Renders as quiet chrome that informs without
 * competing for attention. Purely a legend: it surfaces existing shortcuts and handles no input.
 *
 * Both the grid footer ([GridKeyboardLegend]) and the browser overlay
 * ([BrowserKeyboardLegend]) build on this; the only differences are the surface ([shape],
 * [containerColor]) and key-cap colours, so the two stay one component rather than two that
 * drift apart. The list is an [ImmutableList] so a structurally-equal set of hints lets the
 * strip skip recomposition.
 */
@Composable
fun KeyboardLegend(
    hints: ImmutableList<KeyHint>,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    keyCapContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    keyCapContentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(shape = shape, color = containerColor, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg),
        ) {
            hints.forEach { hint ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
                ) {
                    KeyCap(hint.keys, keyCapContainerColor, keyCapContentColor)
                    Text(
                        text = hint.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        // Keep each hint on one line — a wrapped label fragments into vertical
                        // letters on a narrow window rather than a clean single-line strip.
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyCap(text: String, containerColor: Color, contentColor: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.sm, vertical = 2.dp),
        )
    }
}
