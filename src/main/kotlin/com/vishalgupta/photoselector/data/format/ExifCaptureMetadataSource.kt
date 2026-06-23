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
 * frames, never absolute time. JPEG-only (ExifReader's scope) — HEIC is handled
 * by [HeicCaptureMetadataSource] reading the same fields through ImageIO, and the
 * two are chained in a [CompositeCaptureMetadataSource]. The raw→domain shaping is
 * shared via [toCaptureMetadata] so both sources interpret EXIF identically.
 */
class ExifCaptureMetadataSource : CaptureMetadataSource {

    override fun metadataFor(photo: Photo): CaptureMetadata =
        ExifReader.readCaptureInfo(photo.absolutePath)?.toCaptureMetadata() ?: CaptureMetadata.NONE
}

/**
 * Shapes raw [ExifCaptureInfo] (from [ExifReader] for JPEG, or [com.vishalgupta.photoselector.data
 * .format.macos.MacImageIO] for HEIC) into the domain's [CaptureMetadata]. The timestamp is read in
 * UTC because only inter-frame deltas matter.
 */
internal fun ExifCaptureInfo.toCaptureMetadata(): CaptureMetadata =
    CaptureMetadata(
        takenAtEpochMs = parseExifTakenAt(dateTimeOriginal, subSecTimeOriginal),
        cameraId = exifCameraId(make, model),
        orientation = orientation,
    )

private fun exifCameraId(make: String?, model: String?): String? {
    val parts = listOfNotNull(make?.trim()?.ifEmpty { null }, model?.trim()?.ifEmpty { null })
    return parts.joinToString(" ").ifEmpty { null }
}

private fun parseExifTakenAt(dateTimeOriginal: String?, subSec: String?): Long? {
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

private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
