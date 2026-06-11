package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup

/**
 * Reports grouping progress as the per-photo pass advances: [processed] of [total] photos handled.
 * Grouping reads/derives one fact per photo (EXIF for time, an embedding for similarity), so the
 * grid drives a determinate progress bar off this — the cold similarity pass is a ~minute-long wait.
 */
typealias GroupingProgress = (processed: Int, total: Int) -> Unit

/** A no-op [GroupingProgress] — the default seam argument, and what tests that don't care pass. */
val NoGroupingProgress: GroupingProgress = { _, _ -> }

/**
 * The grid's single grouping seam: turn the scan-ordered flat photo list into a list of
 * [PhotoGroup]s. The grid, browser and Compare/Survey consume the result the same way no matter
 * which strategy produced it, so the heuristic can be swapped without touching presentation.
 *
 * Two strategies exist today, both behind this interface:
 *  - time + camera proximity ([burstGrouper], wrapping [BurstGrouper]) — cheap, EXIF-only;
 *  - visual similarity (`data/ai/SimilarityPhotoGrouper`, over [SimilarityGrouper]) — embeds each
 *    frame, so it must run off-thread, which is why [group] is `suspend`.
 *
 * [group] reports progress through [onProgress] as it advances so the grid can show how far a slow
 * pass has gotten; callers that don't care use the default no-op.
 */
interface PhotoGrouper {
    suspend fun group(
        photos: List<Photo>,
        onProgress: GroupingProgress = NoGroupingProgress,
    ): List<PhotoGroup>
}

/** Adapts the pure [BurstGrouper] (time + camera proximity) to the [PhotoGrouper] seam. */
fun burstGrouper(
    metadataSource: CaptureMetadataSource,
    maxGapMs: Long = BurstGrouper.DEFAULT_MAX_GAP_MS,
): PhotoGrouper = object : PhotoGrouper {
    override suspend fun group(photos: List<Photo>, onProgress: GroupingProgress): List<PhotoGroup> =
        BurstGrouper.group(photos, metadataSource, maxGapMs, onProgress)
}
