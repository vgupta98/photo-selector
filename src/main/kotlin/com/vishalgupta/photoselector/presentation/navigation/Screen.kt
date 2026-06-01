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
}
