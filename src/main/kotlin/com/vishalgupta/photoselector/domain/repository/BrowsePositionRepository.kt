package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.RootFolder

interface BrowsePositionRepository {
    suspend fun save(root: RootFolder, index: Int)
    fun load(root: RootFolder): Int
}
