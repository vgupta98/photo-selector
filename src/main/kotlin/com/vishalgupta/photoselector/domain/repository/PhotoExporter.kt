package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import java.nio.file.Path

enum class ConflictPolicy { OVERWRITE, SKIP, RENAME }

data class CopyReport(
    val copied: Int,
    val skipped: Int,
    val failed: List<Pair<Photo, Throwable>>,
)

interface PhotoExporter {
    suspend fun exportTxt(
        root: RootFolder,
        favourites: List<Photo>,
        destinationTxt: Path,
    )

    suspend fun copyToFolder(
        root: RootFolder,
        favourites: List<Photo>,
        destDir: Path,
        policy: ConflictPolicy = ConflictPolicy.RENAME,
        onProgress: (copied: Int, total: Int) -> Unit = { _, _ -> },
    ): CopyReport
}
