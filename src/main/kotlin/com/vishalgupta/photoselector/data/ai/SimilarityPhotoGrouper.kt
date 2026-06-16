package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.grouping.GroupingProgress
import com.vishalgupta.photoselector.domain.grouping.PhotoGrouper
import com.vishalgupta.photoselector.domain.grouping.SimilarityGrouper
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The visual-similarity [PhotoGrouper]: extracts each frame's [PhotoFeatures] (cached) and feeds
 * the pure [SimilarityGrouper] the resulting embedding + sharpness maps. The data-layer half of
 * similarity grouping — it owns the expensive decode/inference; the clustering decision stays pure
 * and unit-tested in the domain layer.
 *
 * Extraction runs **bounded-parallel**: a cold pass over thousands of frames is the longest
 * operation in the app, and a single coroutine left a multi-core machine mostly idle. Each frame's
 * decode + embedding is an independent task, gated by a [Semaphore] of width [concurrency] so we
 * never oversubscribe the shared decode pool (each in-flight frame holds two `DecodedImage`s — see
 * `AppContainer`). Parallelism does not cost cancellability: every task is a child of one
 * `coroutineScope`, so a re-slice/toggle cancels the whole fan-out structurally and each task still
 * `ensureActive()`s before doing work. The clustering step is pure and order-independent (it looks
 * features up by [PhotoId]), so the result is identical to the old sequential pass.
 */
class SimilarityPhotoGrouper(
    private val extractor: PhotoFeatureExtractor,
    private val concurrency: Int = DEFAULT_CONCURRENCY,
    private val threshold: Float = SimilarityGrouper.DEFAULT_THRESHOLD,
) : PhotoGrouper {

    override suspend fun group(photos: List<Photo>, onProgress: GroupingProgress): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()
        val total = photos.size
        val embeddings = ConcurrentHashMap<PhotoId, FloatArray>(total)
        val sharpness = ConcurrentHashMap<PhotoId, Float>(total)
        // Thread-safe running count: tasks finish out of order, so the bar is driven by how many
        // have completed, not by any one task's index. GridViewModel coalesces this to whole-percent
        // ticks and only ever moves the bar forward (see its onProgress handler).
        val processed = AtomicInteger(0)
        val gate = Semaphore(concurrency.coerceAtLeast(1))

        coroutineScope {
            photos.forEach { photo ->
                launch {
                    gate.withPermit {
                        ensureActive()
                        extractor.featuresFor(photo)?.let { features ->
                            embeddings[photo.id] = features.embedding
                            sharpness[photo.id] = features.sharpness
                        }
                    }
                    onProgress(processed.incrementAndGet(), total)
                }
            }
        }
        // coroutineScope joined all tasks; every surviving feature is in the maps.
        return SimilarityGrouper.group(photos, embeddings, sharpness, threshold)
    }

    private companion object {
        // Standalone default (tests, direct construction). Production wires the decode-pool width
        // from AppContainer so the fan-out exactly fills, and never exceeds, the decode threads.
        const val DEFAULT_CONCURRENCY = 4
    }
}
