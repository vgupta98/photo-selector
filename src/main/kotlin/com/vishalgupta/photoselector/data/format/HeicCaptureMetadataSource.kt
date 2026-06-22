package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.data.format.macos.MacImageIO
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadataSource
import com.vishalgupta.photoselector.domain.model.Photo

/**
 * Capture metadata for HEIC/HEIF, read through the macOS ImageIO bridge ([MacImageIO.readCaptureInfo])
 * because [ExifReader] is JPEG-only. It reads the same raw fields (DateTimeOriginal, camera, orientation)
 * and shapes them with the shared [toCaptureMetadata], so HEIC frames group exactly like JPEG ones.
 *
 * Format-scoped: only `.heic`/`.heif` are handled (reusing [HeicDecoder]'s extension set as the single
 * source of truth); any other file short-circuits to [CaptureMetadata.NONE] *without* touching the
 * native bridge. That makes it composable in a [CompositeCaptureMetadataSource] — the JPEG and HEIC
 * sources are each blind to the other's formats, so the first non-NONE result is unambiguous.
 *
 * macOS-only (the bridge loads the system ImageIO frameworks); off macOS it is simply not registered,
 * mirroring [HeicDecoder]. A read failure degrades to [CaptureMetadata.NONE] — never throws.
 */
class HeicCaptureMetadataSource : CaptureMetadataSource {

    override fun metadataFor(photo: Photo): CaptureMetadata {
        if (!isHeic(photo)) return CaptureMetadata.NONE
        return MacImageIO.readCaptureInfo(photo.absolutePath)?.toCaptureMetadata() ?: CaptureMetadata.NONE
    }

    private fun isHeic(photo: Photo): Boolean {
        val ext = photo.fileName.substringAfterLast('.', "").lowercase()
        return ext in HeicDecoder.Companion.HeicFormat.extensions
    }

    companion object {
        /** True only on macOS, where the ImageIO bridge is usable (same gate as [HeicDecoder]). */
        fun isSupportedOnThisPlatform(): Boolean = MacImageIO.isAvailable()
    }
}
