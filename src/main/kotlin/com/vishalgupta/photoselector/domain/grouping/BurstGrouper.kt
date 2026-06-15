package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import kotlin.math.abs

/**
 * Groups consecutive near-simultaneous frames into [PhotoGroup.Burst]s, leaving
 * everything else as [PhotoGroup.Single]. Operates on the scan-ordered list
 * (sorted by relative path), so a camera's sequential filenames cluster
 * naturally and only adjacent frames are ever merged.
 *
 * Two adjacent frames belong to the same burst iff ALL hold:
 *  - same immediate parent folder (a folder is an event boundary),
 *  - same camera body (EXIF Make+Model; two absent ids count as "same"),
 *  - same orientation (don't merge a portrait into a landscape run),
 *  - both have a real EXIF capture time, within [maxGapMs] of each other.
 *
 * The gap is measured between *adjacent* frames, the way cameras actually fire a
 * burst; a long sequence of sub-gap frames is one run. Derived after scan, never
 * persisted. The similarity-grouping upgrade swaps this heuristic for embedding
 * clustering behind the same [PhotoGroup] return type.
 *
 * A frame without a readable capture time (HEIC today, or any EXIF-less file)
 * never joins a burst — it always stands alone. We deliberately do NOT fall back
 * to file mtime: a bulk copy flattens mtime to near-identical values and would
 * over-group unrelated photos. Reading HEIC capture time (an ImageIO read) is the
 * way to make HEIC bursts group; until then they stay single.
 */
object BurstGrouper {

    const val DEFAULT_MAX_GAP_MS = 2_000L

    fun group(
        photos: List<Photo>,
        metadataSource: CaptureMetadataSource,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
        onProgress: GroupingProgress = NoGroupingProgress,
    ): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()
        // The metadata read is the per-photo cost here, so report progress as each one resolves.
        val total = photos.size
        val metas = photos.mapIndexed { i, photo ->
            metadataSource.metadataFor(photo).also { onProgress(i + 1, total) }
        }

        val groups = ArrayList<PhotoGroup>()
        var runStart = 0
        for (i in 1..photos.size) {
            val breakHere = i == photos.size ||
                !sameBurst(photos[i - 1], metas[i - 1], photos[i], metas[i], maxGapMs)
            if (breakHere) {
                val run = photos.subList(runStart, i)
                groups += if (run.size >= 2) PhotoGroup.Burst(run.toList()) else PhotoGroup.Single(run[0])
                runStart = i
            }
        }
        return groups
    }

    private fun sameBurst(
        a: Photo,
        metaA: CaptureMetadata,
        b: Photo,
        metaB: CaptureMetadata,
        maxGapMs: Long,
    ): Boolean {
        if (a.absolutePath.parent != b.absolutePath.parent) return false
        if (metaA.cameraId != metaB.cameraId) return false
        if (metaA.orientation != metaB.orientation) return false
        // Only ever group on a real capture time. Without it (HEIC today, or any
        // EXIF-less file) we can't tell a burst from an unrelated pair, and file
        // mtime is unreliable: a bulk copy flattens it and over-groups. So a
        // missing capture time on either frame means "not the same burst".
        val timeA = metaA.takenAtEpochMs ?: return false
        val timeB = metaB.takenAtEpochMs ?: return false
        return abs(timeA - timeB) <= maxGapMs
    }
}
