package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Path

class JpegDecoder : PhotoDecoder {
    override val format: PhotoFormat = JpegFormat

    override suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage =
        SkiaImageDecoding.decode(path, targetMaxDimensionPx)

    companion object {
        object JpegFormat : PhotoFormat {
            override val id: String = "jpeg"
            override val extensions: Set<String> = setOf("jpg", "jpeg")
        }
    }
}
