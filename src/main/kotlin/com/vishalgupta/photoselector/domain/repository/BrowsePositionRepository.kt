package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder

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
