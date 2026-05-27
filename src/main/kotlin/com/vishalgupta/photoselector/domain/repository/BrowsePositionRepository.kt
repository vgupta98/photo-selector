package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder

data class BrowsePosition(
    val lastIndex: Int = 0,
    val lastPhotoId: PhotoId? = null,
)

interface BrowsePositionRepository {
    suspend fun save(root: RootFolder, position: BrowsePosition)
    fun load(root: RootFolder): BrowsePosition
}
