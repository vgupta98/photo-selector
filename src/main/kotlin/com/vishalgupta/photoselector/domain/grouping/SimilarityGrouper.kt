package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId

/**
 * Groups consecutive frames whose embeddings are visually close into [PhotoGroup.Burst]s, leaving
 * everything else a [PhotoGroup.Single]. The pure, framework-free core of similarity grouping: it
 * takes precomputed embeddings + sharpness (the expensive decode/inference happens in the data
 * layer, `data/ai`) and decides only the clustering and the suggested-keeper frame.
 *
 * Like [BurstGrouper] it merges only *adjacent* frames in scan order, so every group stays a
 * contiguous run — which the grid's expand-in-place burst UI depends on. Two adjacent frames join
 * the same cluster iff ALL hold:
 *  - same immediate parent folder (a folder is an event boundary, even if pixels look alike),
 *  - both have an embedding of equal length,
 *  - their cosine similarity is at least [threshold].
 *
 * Unlike [BurstGrouper], capture time is irrelevant: two near-identical shots taken 90 seconds
 * apart, or from two different cameras, still group — that is the whole point of the visual lens.
 * A frame with no embedding (decode/inference not yet done) never joins a cluster; it stands alone
 * rather than guessing.
 *
 * Within a cluster the **sharpest** frame is marked as the representative ([PhotoGroup.Burst.keyIndex]),
 * a hint the user can override — never an automatic selection.
 *
 * Embeddings are assumed L2-normalized (see [com.vishalgupta.photoselector.data.ai.EmbeddingModel]),
 * so cosine similarity is a plain dot product.
 */
object SimilarityGrouper {

    /** Cosine-similarity floor for two adjacent frames to count as the same shot. Tunable. */
    const val DEFAULT_THRESHOLD = 0.85f

    fun group(
        photos: List<Photo>,
        embeddings: Map<PhotoId, FloatArray>,
        sharpness: Map<PhotoId, Float> = emptyMap(),
        threshold: Float = DEFAULT_THRESHOLD,
    ): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()

        val groups = ArrayList<PhotoGroup>()
        var runStart = 0
        for (i in 1..photos.size) {
            val breakHere = i == photos.size ||
                !sameShot(photos[i - 1], photos[i], embeddings, threshold)
            if (breakHere) {
                val run = photos.subList(runStart, i)
                groups += if (run.size >= 2) {
                    // keyIsSuggested = true: the key frame is the sharpest, a quality hint the tile can
                    // surface as a "Pick" (a time burst leaves this false — it has no quality signal).
                    PhotoGroup.Burst(run.toList(), keyIndex = sharpestIndex(run, sharpness), keyIsSuggested = true)
                } else {
                    PhotoGroup.Single(run[0])
                }
                runStart = i
            }
        }
        return groups
    }

    private fun sameShot(
        a: Photo,
        b: Photo,
        embeddings: Map<PhotoId, FloatArray>,
        threshold: Float,
    ): Boolean {
        if (a.absolutePath.parent != b.absolutePath.parent) return false
        val embA = embeddings[a.id] ?: return false
        val embB = embeddings[b.id] ?: return false
        if (embA.size != embB.size) return false
        return cosine(embA, embB) >= threshold
    }

    /** Index of the sharpest frame in [run]; the middle frame when no sharpness is known (neutral, matches a time burst). */
    private fun sharpestIndex(run: List<Photo>, sharpness: Map<PhotoId, Float>): Int {
        var best = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in run.indices) {
            val score = sharpness[run[i].id] ?: continue
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return if (best >= 0) best else run.size / 2
    }

    /** Dot product; equals cosine similarity for L2-normalized inputs. */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
