package com.vishalgupta.photoselector.presentation.navigation

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId

/** Which subset of photos the browser is paging through. */
sealed interface BrowseScope {
    data object AllPhotos : BrowseScope
    data object FavouritesOnly : BrowseScope
}

/**
 * Single source of truth for the photo slice shown under a given scope.
 * Both the Grid and the Browser must agree on this ordering — a click at
 * index N in the grid must land on photo N in the browser's list.
 */
fun BrowseScope.slice(allPhotos: List<Photo>, favIds: Set<PhotoId>): List<Photo> =
    when (this) {
        BrowseScope.AllPhotos -> allPhotos
        BrowseScope.FavouritesOnly -> allPhotos.filter { it.id in favIds }
    }
