package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.data.format.macos.MacImageIO
import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.file.Path

/**
 * Decodes camera RAW files (CR2/CR3, ARW, NEF/NRW, RAF, DNG, RW2, ORF) for culling via the macOS
 * ImageIO RAW codec, reusing the [MacImageIO] bridge [HeicDecoder] also rides — no new dependency,
 * no bundled native binary. ImageIO renders the camera's embedded preview with EXIF orientation
 * baked in, into [DecodedImage]'s sRGB BGRA-premultiplied contract.
 *
 * Unlike [HeicDecoder], RAW decodes from the local file *path* ([MacImageIO.decodeFileToBgra]) rather
 * than reading the file into a byte buffer first: the macOS RAW codec returns an empty source for
 * several makers (e.g. Sony ARW) when handed bytes, and falls back to the tiny embedded thumbnail for
 * Nikon NEF — letting ImageIO open the file itself decodes the full preview for every maker. (This is
 * about how the on-disk file is read, not where it lives — RAW files are local like any other photo.)
 * See [MacImageIO] for the why.
 *
 * v1 is preview-only: we never demosaic or apply white balance ourselves — that's what ImageIO's
 * RAW codec is for, and a full RAW pipeline is explicitly out of scope. macOS-only, like
 * [HeicDecoder]; a future Windows build registers its own RAW decoder behind [PhotoDecoder] without
 * touching the rest of the pipeline.
 */
class RawDecoder : PhotoDecoder {
    override val format: PhotoFormat = RawFormat

    override suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage =
        MacImageIO.decodeFileToBgra(path, targetMaxDimensionPx)

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
