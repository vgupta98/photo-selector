package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.grouping.PhotoGrouper
import com.vishalgupta.photoselector.domain.grouping.SimilarityGrouper
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * The visual-similarity [PhotoGrouper]: extracts each frame's [PhotoFeatures] (cached) and feeds
 * the pure [SimilarityGrouper] the resulting embedding + sharpness maps. The data-layer half of
 * similarity grouping — it owns the expensive decode/inference; the clustering decision stays pure
 * and unit-tested in the domain layer.
 *
 * Runs the photos sequentially so it stays cancellable between frames (the grid cancels and
 * re-runs grouping on every re-slice and on the toolbar toggle); the per-photo cost is dominated
 * by the cached decode, so a cold first pass is the only slow one.
 */
class SimilarityPhotoGrouper(
    private val extractor: PhotoFeatureExtractor,
    private val threshold: Float = SimilarityGrouper.DEFAULT_THRESHOLD,
) : PhotoGrouper {

    override suspend fun group(photos: List<Photo>): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()
        val embeddings = HashMap<PhotoId, FloatArray>(photos.size)
        val sharpness = HashMap<PhotoId, Float>(photos.size)
        for (photo in photos) {
            coroutineContext.ensureActive()
            val features = extractor.featuresFor(photo) ?: continue
            embeddings[photo.id] = features.embedding
            sharpness[photo.id] = features.sharpness
        }
        return SimilarityGrouper.group(photos, embeddings, sharpness, threshold)
    }
}
