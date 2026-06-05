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
     * list (same list the browser pages through), so exiting back to the browser lands on the
     * active pane's photo. [returnScrollIndex] is carried through from the source browser so the
     * All-Photos scroll position survives the round trip, exactly as [Browser] does.
     */
    data class Compare(
        val root: RootFolder,
        val scope: CategoryScope = CategoryScope.AllPhotos,
        val leftIndex: Int,
        val rightIndex: Int,
        val returnScrollIndex: Int? = null,
    ) : Screen
}
