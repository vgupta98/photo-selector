package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.designsystem.theme.PillShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The Inspect grid's keyboard legend, styled to match the browser overlay chrome so the two read as
 * one surface. Hints are truthful to the grid key handler: `Tab` and the arrows move the active tile,
 * filing keys apply to the active tile, `Enter` opens it in browse mode ([canBrowse]), `Esc` returns
 * to the grid. Filing hints (`F`, `1-9`) drop in [readOnly], and `1-9` only shows when there are
 * [hasCustomCategories] custom categories.
 */
@Composable
fun SurveyKeyboardLegend(
    hasCustomCategories: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    // True when this grid is a facet of Inspect: surfaces the `Enter` hint that opens the active
    // tile in browse mode. False elsewhere, where that toggle doesn't exist.
    canBrowse: Boolean = false,
) {
    KeyboardLegend(
        hints = surveyHints(
            hasCustomCategories = hasCustomCategories,
            readOnly = readOnly,
            canBrowse = canBrowse,
        ),
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
    canBrowse: Boolean,
): ImmutableList<KeyHint> = buildList {
    add(KeyHint("Tab ← →", "Move"))
    if (!readOnly) {
        add(KeyHint("F", "Favourite"))
        if (hasCustomCategories) add(KeyHint("1–9", "Categories"))
    }
    if (canBrowse) add(KeyHint("Enter", "Browse"))
    add(KeyHint("Esc", "Back"))
}.toImmutableList()
