package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.repository.PhotoTrash
import com.vishalgupta.photoselector.domain.repository.TrashReport

class MovePhotosToTrashUseCase(private val trash: PhotoTrash) {
    suspend operator fun invoke(photos: List<Photo>): TrashReport = trash.moveToTrash(photos)
}
