package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadataSource
import com.vishalgupta.photoselector.domain.model.Photo
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * EXIF-backed [CaptureMetadataSource]. Reads DateTimeOriginal / SubSecTimeOriginal,
 * Make, Model and Orientation from a JPEG's EXIF block via [ExifReader] and
 * shapes them into the domain's [CaptureMetadata].
 *
 * Non-JPEG or EXIF-less files yield [CaptureMetadata.NONE]; the timestamp is
 * interpreted in UTC because the grouper only ever compares deltas between
 * frames, never absolute time. Currently JPEG-only (ExifReader's scope), so HEIC
 * yields [CaptureMetadata.NONE] and the grouper leaves it ungroupable (a lone
 * Single) — there is no mtime fallback. Grouping HEIC means reading its capture
 * time via an ImageIO read, not loosening the heuristic.
 */
class ExifCaptureMetadataSource : CaptureMetadataSource {

    override fun metadataFor(photo: Photo): CaptureMetadata {
        val info = ExifReader.readCaptureInfo(photo.absolutePath) ?: return CaptureMetadata.NONE
        return CaptureMetadata(
            takenAtEpochMs = parseTakenAt(info.dateTimeOriginal, info.subSecTimeOriginal),
            cameraId = cameraId(info.make, info.model),
            orientation = info.orientation,
        )
    }

    private fun cameraId(make: String?, model: String?): String? {
        val parts = listOfNotNull(make?.trim()?.ifEmpty { null }, model?.trim()?.ifEmpty { null })
        return parts.joinToString(" ").ifEmpty { null }
    }

    private fun parseTakenAt(dateTimeOriginal: String?, subSec: String?): Long? {
        if (dateTimeOriginal == null) return null
        // A blank/placeholder EXIF date ("    :  :     :  :  ", common in stripped files) fails to
        // parse and falls through to null here - treated as no capture time, so the frame won't group.
        val base = runCatching { LocalDateTime.parse(dateTimeOriginal.trim(), EXIF_FORMAT) }
            .getOrNull() ?: return null
        var ms = base.toInstant(ZoneOffset.UTC).toEpochMilli()
        val digits = subSec?.trim()?.takeWhile { it.isDigit() }
        if (!digits.isNullOrEmpty()) {
            ("0.$digits").toDoubleOrNull()?.let { frac -> ms += (frac * 1000).toLong() }
        }
        return ms
    }

    private companion object {
        val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
