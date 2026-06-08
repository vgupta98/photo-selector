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
 *  - shot within [maxGapMs] of each other (EXIF DateTimeOriginal preferred;
 *    file mtime fallback when either frame lacks a capture time).
 *
 * The gap is measured between *adjacent* frames, the way cameras actually fire a
 * burst; a long sequence of sub-gap frames is one run. Derived after scan, never
 * persisted. The similarity-grouping upgrade swaps this heuristic for embedding
 * clustering behind the same [PhotoGroup] return type.
 *
 * Caveat: when frames lack EXIF time, grouping leans on mtime, which a bulk copy
 * can flatten to near-identical values — that over-grouping is the known limit
 * the similarity upgrade is meant to fix.
 */
object BurstGrouper {

    const val DEFAULT_MAX_GAP_MS = 2_000L

    fun group(
        photos: List<Photo>,
        metadataSource: CaptureMetadataSource,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    ): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()
        val metas = photos.map(metadataSource::metadataFor)

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
        val (timeA, timeB) = if (metaA.takenAtEpochMs != null && metaB.takenAtEpochMs != null) {
            metaA.takenAtEpochMs to metaB.takenAtEpochMs
        } else {
            a.lastModifiedEpochMs to b.lastModifiedEpochMs
        }
        return abs(timeA - timeB) <= maxGapMs
    }
}
