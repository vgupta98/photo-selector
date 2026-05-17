package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import java.nio.file.Path

class ExportFavouritesTxtUseCase(private val exporter: PhotoExporter) {
    suspend operator fun invoke(
        root: RootFolder,
        favourites: List<Photo>,
        destinationTxt: Path,
    ) {
        exporter.exportTxt(root, favourites, destinationTxt)
    }
}
