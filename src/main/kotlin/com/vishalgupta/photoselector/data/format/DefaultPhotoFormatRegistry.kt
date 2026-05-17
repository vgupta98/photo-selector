package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
import java.nio.file.Path
import kotlin.io.path.extension

class DefaultPhotoFormatRegistry(
    decoders: List<PhotoDecoder>,
) : PhotoFormatRegistry {

    private val decodersByExt: Map<String, PhotoDecoder> = buildMap {
        decoders.forEach { decoder ->
            decoder.format.extensions.forEach { ext ->
                put(ext.lowercase(), decoder)
            }
        }
    }

    override val supportedFormats: List<PhotoFormat> = decoders.map { it.format }.distinctBy { it.id }

    override fun isSupported(path: Path): Boolean = path.extension.lowercase() in decodersByExt

    override fun decoderFor(path: Path): PhotoDecoder? = decodersByExt[path.extension.lowercase()]
}
