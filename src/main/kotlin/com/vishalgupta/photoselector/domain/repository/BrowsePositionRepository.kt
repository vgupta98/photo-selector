package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder

/**
 * Where the user left off in a folder, persisted across launches.
 *
 * `lastIndex` is shared between two producers and is always interpreted in
 * the AllPhotos ordering:
 *  - the grid writes its `firstVisibleItemIndex` (where the user last
 *    scrolled in the AllPhotos thumbnail grid), and
 *  - the browser writes its current photo's index when scope == AllPhotos.
 * In FavouritesOnly scope the browser only updates `lastPhotoId` and leaves
 * `lastIndex` alone, because a favourites-list index is meaningless in the
 * AllPhotos space. Re-entry restores the grid by index and the
 * "last viewed" marker by id, so both producers writing the same field is
 * deliberate — don't repurpose `lastIndex` to mean "the photo I last viewed"
 * or it'll diverge from the grid's scroll position.
 */
data class BrowsePosition(
    val lastIndex: Int = 0,
    val lastPhotoId: PhotoId? = null,
)

interface BrowsePositionRepository {
    suspend fun save(root: RootFolder, position: BrowsePosition)
    suspend fun saveIndex(root: RootFolder, index: Int)
    suspend fun saveLastPhotoId(root: RootFolder, photoId: PhotoId?)
    fun load(root: RootFolder): BrowsePosition
}
