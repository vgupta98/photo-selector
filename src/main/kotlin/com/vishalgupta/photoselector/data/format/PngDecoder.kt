package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Path

class PngDecoder : PhotoDecoder {
    override val format: PhotoFormat = PngFormat

    override suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage =
        SkiaImageDecoding.decode(path, targetMaxDimensionPx)

    companion object {
        object PngFormat : PhotoFormat {
            override val id: String = "png"
            override val extensions: Set<String> = setOf("png")
        }
    }
}
