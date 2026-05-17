package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun scan(root: RootFolder): Flow<ScanProgress>
}
