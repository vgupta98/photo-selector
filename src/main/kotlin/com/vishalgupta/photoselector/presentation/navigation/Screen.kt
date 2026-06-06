package com.vishalgupta.photoselector.presentation.navigation

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder

sealed interface Screen {
    data object RootPicker : Screen
    data class Grid(
        val root: RootFolder,
        val scope: CategoryScope = CategoryScope.AllPhotos,
        val initialScrollIndex: Int = 0,
        val lastViewedPhotoId: PhotoId? = null,
        val returnScrollIndex: Int? = null,
    ) : Screen
    data class Browser(
        val root: RootFolder,
        val initialIndex: Int = 0,
        val scope: CategoryScope = CategoryScope.AllPhotos,
        // Carried through so backing out of the browser into a category grid, then to All
        // Photos, restores the All-Photos scroll position (PR #32 review Q#2).
        val returnScrollIndex: Int? = null,
    ) : Screen
    /**
     * Two-up side-by-side compare. [leftIndex] / [rightIndex] index into the scoped photo
     * list (same list the browser pages through), so exiting lands on the active pane's photo.
     * [returnScrollIndex] is carried through from the source so the scroll position survives the
     * round trip, exactly as [Browser] does. [returnToGrid] flags a grid-originated compare (the
     * `C` shortcut over a 2-tile selection): it exits back to the grid at [returnScrollIndex]
     * rather than into the full-screen browser, since that's where the user came from.
     */
    data class Compare(
        val root: RootFolder,
        val scope: CategoryScope = CategoryScope.AllPhotos,
        val leftIndex: Int,
        val rightIndex: Int,
        val returnScrollIndex: Int? = null,
        val returnToGrid: Boolean = false,
    ) : Screen
    /**
     * Survey: an overview-pick grid of the [indices] (3+ tiles) selected in the grid and opened
     * with `C`. Each index points into the scoped photo list (same as [Compare]). One tile is
     * "active"; arrows/Tab move it and `F`/`1`-`9` file it, mirroring compare's active-pane model
     * but with no zoom. [returnScrollIndex] restores the grid's scroll on exit.
     */
    data class Survey(
        val root: RootFolder,
        val scope: CategoryScope = CategoryScope.AllPhotos,
        val indices: List<Int>,
        val returnScrollIndex: Int? = null,
    ) : Screen
}
