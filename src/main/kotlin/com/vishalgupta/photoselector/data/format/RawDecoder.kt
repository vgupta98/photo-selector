package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.data.format.macos.MacImageIO
import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decodes camera RAW files (CR2/CR3, ARW, NEF/NRW, RAF, DNG, RW2, ORF) for culling. macOS ImageIO
 * has a built-in RAW codec for every one of these, so this reuses the exact [MacImageIO] bridge
 * [HeicDecoder] already uses — no new dependency, no bundled native binary. ImageIO extracts the
 * camera's embedded preview (or does a system RAW decode) and bakes in EXIF orientation, then we
 * render into [DecodedImage]'s sRGB BGRA-premultiplied contract. The decode/orientation path is
 * therefore identical to (and covered by) the HEIC tests.
 *
 * v1 is preview-only: we never demosaic or apply white balance ourselves — that's what ImageIO's
 * RAW codec is for, and a full RAW pipeline is explicitly out of scope. macOS-only, like
 * [HeicDecoder]; a future Windows build registers its own RAW decoder behind [PhotoDecoder] without
 * touching the rest of the pipeline.
 */
class RawDecoder : PhotoDecoder {
    override val format: PhotoFormat = RawFormat

    override suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage {
        val bytes = Files.readAllBytes(path)
        return MacImageIO.decodeToBgra(bytes, targetMaxDimensionPx)
    }

    companion object {
        /** True only on macOS, where the ImageIO RAW codec is usable. */
        fun isSupportedOnThisPlatform(): Boolean = MacImageIO.isAvailable()

        object RawFormat : PhotoFormat {
            override val id: String = "raw"

            // Union of the major camera makers' RAW extensions, in maker order:
            // Canon, Sony, Nikon, Fujifilm, Adobe/multi-vendor, Panasonic, Olympus.
            override val extensions: Set<String> = setOf(
                "cr2", "cr3", // Canon
                "arw",        // Sony
                "nef", "nrw", // Nikon
                "raf",        // Fujifilm
                "dng",        // Adobe / multi-vendor
                "rw2",        // Panasonic
                "orf",        // Olympus
            )
        }
    }
}
