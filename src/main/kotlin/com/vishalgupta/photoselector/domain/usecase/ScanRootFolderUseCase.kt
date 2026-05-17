package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.model.ScanProgress
import com.vishalgupta.photoselector.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow

class ScanRootFolderUseCase(private val repository: PhotoRepository) {
    operator fun invoke(root: RootFolder): Flow<ScanProgress> = repository.scan(root)
}
