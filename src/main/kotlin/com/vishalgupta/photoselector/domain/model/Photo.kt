package com.vishalgupta.photoselector.domain.model

import java.nio.file.Path

data class Photo(
    val id: PhotoId,
    val absolutePath: Path,
    val relativePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
)
