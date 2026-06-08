package com.vishalgupta.photoselector.domain.grouping

/**
 * Per-photo capture facts the burst grouper compares. Every field is nullable:
 * a photo with no readable EXIF (a PNG, a HEIC, a stripped JPEG) yields [NONE]
 * and the grouper falls back to file mtime and the containing folder.
 */
data class CaptureMetadata(
    /**
     * Shot instant in epoch millis, derived from EXIF DateTimeOriginal (+
     * SubSecTimeOriginal). Interpreted in UTC — only deltas between frames
     * matter, never the absolute wall-clock time. Null when absent/unparseable.
     */
    val takenAtEpochMs: Long?,
    /** Camera identity (EXIF Make + Model), or null when absent. */
    val cameraId: String?,
    /** EXIF orientation (1..8), or null when absent. */
    val orientation: Int?,
) {
    companion object {
        val NONE = CaptureMetadata(takenAtEpochMs = null, cameraId = null, orientation = null)
    }
}
