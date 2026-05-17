package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveFavouritesUseCase(private val repository: FavouritesRepository) {
    operator fun invoke(root: RootFolder): StateFlow<Set<PhotoId>> = repository.observe(root)
}
