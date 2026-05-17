package com.vishalgupta.photoselector.domain.format

import java.nio.file.Path

interface PhotoFormatRegistry {
    val supportedFormats: List<PhotoFormat>
    fun isSupported(path: Path): Boolean
    fun decoderFor(path: Path): PhotoDecoder?
}
