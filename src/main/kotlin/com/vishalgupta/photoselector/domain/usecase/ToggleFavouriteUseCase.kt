package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository

class ToggleFavouriteUseCase(private val repository: FavouritesRepository) {
    suspend operator fun invoke(root: RootFolder, id: PhotoId): Boolean =
        repository.toggle(root, id)
}
