package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.RootFolder

interface BrowsePositionRepository {
    fun save(root: RootFolder, index: Int)
    fun load(root: RootFolder): Int
}
