package com.vishalgupta.photoselector.presentation.navigation

/** Which subset of photos the browser is paging through. */
sealed interface BrowseScope {
    data object AllPhotos : BrowseScope
    data object FavouritesOnly : BrowseScope
}
