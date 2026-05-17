package com.vishalgupta.photoselector.domain.usecase

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import java.nio.file.Path

class CopyFavouritesToFolderUseCase(private val exporter: PhotoExporter) {
    suspend operator fun invoke(
        root: RootFolder,
        favourites: List<Photo>,
        destDir: Path,
        policy: ConflictPolicy = ConflictPolicy.RENAME,
        onProgress: (copied: Int, total: Int) -> Unit = { _, _ -> },
    ): CopyReport = exporter.copyToFolder(root, favourites, destDir, policy, onProgress)
}
