package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.data.format.macos.MacImageIO
import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decodes HEIC/HEIF via the macOS ImageIO frameworks (see [MacImageIO]). skiko cannot decode these
 * formats, and there is no maintained cross-platform JVM HEIC library; this impl sits behind
 * [PhotoDecoder] so a future Windows build can register its own HEIC decoder without touching the
 * rest of the pipeline. Output matches [DecodedImage]'s sRGB BGRA-premultiplied contract.
 */
class HeicDecoder : PhotoDecoder {
    override val format: PhotoFormat = HeicFormat

    override suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage {
        val bytes = Files.readAllBytes(path)
        return MacImageIO.decodeToBgra(bytes, targetMaxDimensionPx)
    }

    companion object {
        /** True only on macOS, where the ImageIO bridge is usable. */
        fun isSupportedOnThisPlatform(): Boolean = MacImageIO.isAvailable()

        object HeicFormat : PhotoFormat {
            override val id: String = "heic"
            override val extensions: Set<String> = setOf("heic", "heif")
        }
    }
}
