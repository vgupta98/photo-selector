package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo

/**
 * Supplies [CaptureMetadata] for a photo. The grouper depends only on this
 * domain interface; the EXIF-reading implementation lives in the data layer
 * (`data/format/ExifCaptureMetadataSource`), and tests pass a deterministic
 * fake. Implementations must never throw — return [CaptureMetadata.NONE] when
 * nothing can be read.
 */
fun interface CaptureMetadataSource {
    fun metadataFor(photo: Photo): CaptureMetadata
}
