package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.coroutines.flow.StateFlow

interface FavouritesRepository {
    /** Returns a hot flow of favourite ids for the given root. Re-binds context on root switch. */
    fun observe(root: RootFolder): StateFlow<Set<PhotoId>>

    suspend fun toggle(root: RootFolder, id: PhotoId): Boolean

    suspend fun clearContext()

    /** True when the favourites file cannot be written (e.g. read-only volume). */
    fun isReadOnly(root: RootFolder): StateFlow<Boolean>
}
