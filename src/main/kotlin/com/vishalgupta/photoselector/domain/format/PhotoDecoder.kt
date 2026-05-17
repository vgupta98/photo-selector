package com.vishalgupta.photoselector.domain.format

import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Path

interface PhotoDecoder {
    val format: PhotoFormat

    suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage
}
