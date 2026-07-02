package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import java.nio.file.Path

enum class ConflictPolicy { OVERWRITE, SKIP, RENAME }

data class CopyReport(
    val copied: Int,
    val skipped: Int,
    val failed: List<Pair<Photo, Throwable>>,
)

/**
 * Outcome of an XMP-sidecar export: [written] sidecars produced, [skipped] photos that carried no
 * rating/label under the precedence rule (nothing to hand off), and any per-photo [failed] writes.
 */
data class XmpReport(
    val written: Int,
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

    /**
     * Writes an XMP sidecar next to each photo (`IMG_1234.CR2` -> `IMG_1234.xmp`) carrying the
     * cull's keep/reject decision as `xmp:Rating` / `xmp:Label` for a Lightroom / Capture One
     * handoff. [favouriteIds] and [rejectedIds] drive the rating under a documented precedence rule
     * (reject wins); a photo in neither gets no sidecar.
     */
    suspend fun exportXmpSidecars(
        root: RootFolder,
        photos: List<Photo>,
        favouriteIds: Set<PhotoId>,
        rejectedIds: Set<PhotoId>,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
    ): XmpReport
}
