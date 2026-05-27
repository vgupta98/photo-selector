package com.vishalgupta.photoselector.presentation.navigation

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder

sealed interface Screen {
    data object RootPicker : Screen
    data class Grid(
        val root: RootFolder,
        val scope: BrowseScope = BrowseScope.AllPhotos,
        val initialScrollIndex: Int = 0,
        val lastViewedPhotoId: PhotoId? = null,
    ) : Screen
    data class Browser(
        val root: RootFolder,
        val initialIndex: Int = 0,
        val scope: BrowseScope = BrowseScope.AllPhotos,
    ) : Screen
}
