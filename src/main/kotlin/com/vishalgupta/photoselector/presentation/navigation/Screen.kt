package com.vishalgupta.photoselector.presentation.navigation

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import java.nio.file.Path

/**
 * Identity of a retained grid: one live [com.vishalgupta.photoselector.presentation.grid.GridViewModel]
 * and one scroll position are kept per (root, scope) for the session, so a Grid -> Browser -> Grid
 * round trip returns to the grid exactly as it was left rather than rebuilding (and re-anchoring,
 * which lost the precise offset and fought the regroup reshape). Keyed by [Path] (not [RootFolder])
 * so it stays a stable value key across rescans.
 */
data class GridRetentionKey(val rootPath: Path, val scope: CategoryScope)

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

/**
 * Upper bound on how many photos `C` can open side by side at once (Compare is always 2; Survey
 * takes 3 up to this). Past it the grid declines and toasts instead of opening: the survey grid is
 * non-lazy and pins every tile's decode, and beyond a dozen the tiles are too small to pick between
 * anyway — so a stray `Cmd+A` then `C` can't freeze the app or blow the image cache.
 */
const val MAX_SURVEY_PHOTOS = 12
