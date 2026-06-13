package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

/**
 * The grid's always-on footer legend: a full-width [KeyboardLegend] on the opaque surface. The
 * grid sits on its own background, so the default theme surface reads correctly here.
 */
@Composable
fun GridKeyboardLegend(
    hints: ImmutableList<KeyHint>,
    modifier: Modifier = Modifier,
) {
    KeyboardLegend(
        hints = hints,
        modifier = modifier.fillMaxWidth(),
    )
}
