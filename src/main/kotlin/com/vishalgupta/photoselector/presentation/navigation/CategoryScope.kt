package com.vishalgupta.photoselector.presentation.navigation

import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId

/** Which subset of photos a grid / browser is showing: everything, or one category. */
sealed interface CategoryScope {
    data object AllPhotos : CategoryScope
    data class Category(val id: CategoryId) : CategoryScope
}

/**
 * The category whose membership the focused-tile toggle and the tile star act on: the
 * scoped category, or the built-in Favourites when viewing All Photos (so F/Space stays
 * back-compatible there).
 */
val CategoryScope.activeCategoryId: CategoryId
    get() = when (this) {
        CategoryScope.AllPhotos -> Category.FAVOURITES_ID
        is CategoryScope.Category -> id
    }

/**
 * Single source of truth for the photo slice shown under a given scope. Both the Grid
 * and the Browser must agree on this ordering — a click at index N in the grid must land
 * on photo N in the browser's list. [members] is a pre-resolved set, never the repo: a
 * future smart category resolves its members upstream so this stays predicate-blind.
 */
fun CategoryScope.slice(allPhotos: List<Photo>, members: Set<PhotoId>): List<Photo> =
    when (this) {
        CategoryScope.AllPhotos -> allPhotos
        is CategoryScope.Category -> allPhotos.filter { it.id in members }
    }
