package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup

/**
 * The grid's single grouping seam: turn the scan-ordered flat photo list into a list of
 * [PhotoGroup]s. The grid, browser and Compare/Survey consume the result the same way no matter
 * which strategy produced it, so the heuristic can be swapped without touching presentation.
 *
 * Two strategies exist today, both behind this interface:
 *  - time + camera proximity ([burstGrouper], wrapping [BurstGrouper]) — cheap, EXIF-only;
 *  - visual similarity (`data/ai/SimilarityPhotoGrouper`, over [SimilarityGrouper]) — embeds each
 *    frame, so it must run off-thread, which is why [group] is `suspend`.
 */
fun interface PhotoGrouper {
    suspend fun group(photos: List<Photo>): List<PhotoGroup>
}

/** Adapts the pure [BurstGrouper] (time + camera proximity) to the [PhotoGrouper] seam. */
fun burstGrouper(
    metadataSource: CaptureMetadataSource,
    maxGapMs: Long = BurstGrouper.DEFAULT_MAX_GAP_MS,
): PhotoGrouper = PhotoGrouper { photos -> BurstGrouper.group(photos, metadataSource, maxGapMs) }
