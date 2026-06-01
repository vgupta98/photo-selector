package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import java.nio.file.Path

class ExportPhotosTxtUseCase(private val exporter: PhotoExporter) {
    suspend operator fun invoke(
        root: RootFolder,
        photos: List<Photo>,
        destinationTxt: Path,
    ) {
        exporter.exportTxt(root, photos, destinationTxt)
    }
}
