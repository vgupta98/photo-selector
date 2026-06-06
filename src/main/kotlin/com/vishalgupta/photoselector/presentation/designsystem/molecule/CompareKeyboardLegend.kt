package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.designsystem.theme.PillShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The compare view's keyboard legend, styled to match the browser overlay chrome (translucent
 * black pill, white text) so the two read as one surface. Hints are truthful to the compare key
 * handler: `Tab` switches the active pane, `← →` substitutes the active pane's photo, filing keys
 * apply to the active pane. Filing hints (`F`, `1–9`) drop in [readOnly], and `1–9` only shows when
 * there are [hasCustomCategories] custom categories.
 */
@Composable
fun CompareKeyboardLegend(
    hasCustomCategories: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    KeyboardLegend(
        hints = compareHints(hasCustomCategories = hasCustomCategories, readOnly = readOnly),
        modifier = modifier,
        shape = PillShape,
        containerColor = AppTheme.colors.overlayChromeBackground,
        contentColor = AppTheme.colors.onOverlayChrome,
        keyCapContainerColor = AppTheme.colors.overlayChromeInactiveFill,
        keyCapContentColor = AppTheme.colors.onOverlayChrome,
    )
}

private fun compareHints(
    hasCustomCategories: Boolean,
    readOnly: Boolean,
): ImmutableList<KeyHint> = buildList {
    add(KeyHint("Tab", "Switch"))
    add(KeyHint("← →", "Substitute"))
    if (!readOnly) {
        add(KeyHint("F", "Favourite"))
        if (hasCustomCategories) add(KeyHint("1–9", "Categories"))
    }
    add(KeyHint("+ − 0", "Zoom"))
    add(KeyHint("Esc", "Back"))
}.toImmutableList()
