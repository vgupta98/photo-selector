package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadataSource
import com.vishalgupta.photoselector.domain.model.Photo

/**
 * Fans a photo across several format-scoped [CaptureMetadataSource]s and returns the first non-[NONE]
 * result, the same plug-in shape the decoder registry uses for pixels. Each delegate is blind to the
 * other's formats (the JPEG source returns [NONE] for a HEIC and vice versa), so order only affects
 * cost, not correctness — cheapest/most-common first. All-[NONE] (e.g. a PNG) yields [NONE].
 *
 * A future Windows HEIC reader, or RAW capture time, slots in by adding to the list — no caller change.
 */
class CompositeCaptureMetadataSource(
    private val sources: List<CaptureMetadataSource>,
) : CaptureMetadataSource {

    override fun metadataFor(photo: Photo): CaptureMetadata {
        for (source in sources) {
            val metadata = source.metadataFor(photo)
            if (metadata != CaptureMetadata.NONE) return metadata
        }
        return CaptureMetadata.NONE
    }
}
