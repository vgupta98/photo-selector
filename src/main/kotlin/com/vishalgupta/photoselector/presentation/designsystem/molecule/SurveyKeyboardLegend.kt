package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.designsystem.theme.PillShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The survey view's keyboard legend, styled to match the browser/compare overlay chrome so the
 * modes read as one surface. Hints are truthful to the survey key handler: `Tab` and the arrows
 * move the active tile, filing keys apply to the active tile, `Esc` returns to the grid. Filing
 * hints (`F`, `1-9`) drop in [readOnly], and `1-9` only shows when there are [hasCustomCategories]
 * custom categories.
 */
@Composable
fun SurveyKeyboardLegend(
    hasCustomCategories: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    KeyboardLegend(
        hints = surveyHints(hasCustomCategories = hasCustomCategories, readOnly = readOnly),
        modifier = modifier,
        shape = PillShape,
        containerColor = AppTheme.colors.overlayChromeBackground,
        contentColor = AppTheme.colors.onOverlayChrome,
        keyCapContainerColor = AppTheme.colors.overlayChromeInactiveFill,
        keyCapContentColor = AppTheme.colors.onOverlayChrome,
    )
}

private fun surveyHints(
    hasCustomCategories: Boolean,
    readOnly: Boolean,
): ImmutableList<KeyHint> = buildList {
    add(KeyHint("Tab ← →", "Move"))
    if (!readOnly) {
        add(KeyHint("F", "Favourite"))
        if (hasCustomCategories) add(KeyHint("1–9", "Categories"))
    }
    add(KeyHint("Esc", "Back"))
}.toImmutableList()
