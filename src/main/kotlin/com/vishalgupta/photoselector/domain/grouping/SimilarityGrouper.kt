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
 *  - their cosine similarity is at least the run's cut (see [ThresholdRule]).
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
 *
 * ## The cut is per-event, not global
 * A single fixed cosine floor is the wrong tool: measured on a labelled real-wedding set, each
 * event wants a different cut (0.82–0.90) because "how similar do two
 * frames look when they're the *same* moment" shifts with lens, lighting and subject. The grouper
 * therefore derives the cut **per contiguous same-folder run** from that run's own adjacent-pair
 * cosine distribution via a [ThresholdRule]. The shipped default, [Adaptive], cut F1 from 0.61 to
 * 0.70 on that set while improving precision *and* recall — and is unsupervised (it never reads the
 * labels, only the run's cosine spread). [fixed] keeps the old constant-floor behaviour for the eval
 * sweep and the mechanics unit tests.
 */
object SimilarityGrouper {

    /** Legacy constant cosine floor. Retained for the eval threshold sweep and via [fixed]. */
    const val DEFAULT_THRESHOLD = 0.85f

    /** How much more similar than the run's median a pair must be to join (see [Adaptive]). */
    const val ADAPTIVE_MARGIN = 0.07f

    /** The adaptive cut is clamped here so a pathological run (all-burst or all-singles) can't run away. */
    const val MIN_THRESHOLD = 0.78f
    const val MAX_THRESHOLD = 0.95f

    /** Chooses the cosine cut for one contiguous same-folder run from that run's adjacent-pair cosines. */
    fun interface ThresholdRule {
        fun cut(adjacentCosines: List<Float>): Float
    }

    /** Constant cut, ignoring the distribution — the original behaviour. */
    fun fixed(threshold: Float = DEFAULT_THRESHOLD): ThresholdRule = ThresholdRule { threshold }

    /**
     * Per-run adaptive cut: a pair joins iff it is at least [ADAPTIVE_MARGIN] more similar than the
     * run's **median** adjacent-pair cosine — a burst stands out above the folder's typical neighbour
     * — clamped to [[MIN_THRESHOLD], [MAX_THRESHOLD]]. An empty run (no comparable pairs) cuts at
     * [MAX_THRESHOLD], i.e. groups nothing. This is the shipped default; see the class KDoc for why.
     */
    val Adaptive: ThresholdRule = ThresholdRule { cosines ->
        if (cosines.isEmpty()) MAX_THRESHOLD
        else (median(cosines) + ADAPTIVE_MARGIN).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    fun group(
        photos: List<Photo>,
        embeddings: Map<PhotoId, FloatArray>,
        sharpness: Map<PhotoId, Float> = emptyMap(),
        rule: ThresholdRule = Adaptive,
    ): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()

        val groups = ArrayList<PhotoGroup>()
        var runStart = 0
        // Split into maximal contiguous same-folder runs; each run gets its own cut from its own
        // cosine spread, then clusters internally. Folders are event boundaries the grouper never
        // crosses, so this only changes the cut — never which frames could group.
        while (runStart < photos.size) {
            var runEnd = runStart + 1
            while (runEnd < photos.size &&
                photos[runEnd].absolutePath.parent == photos[runStart].absolutePath.parent
            ) {
                runEnd++
            }
            val folderRun = photos.subList(runStart, runEnd)
            val threshold = rule.cut(adjacentCosines(folderRun, embeddings))
            clusterRun(folderRun, embeddings, sharpness, threshold, into = groups)
            runStart = runEnd
        }
        return groups
    }

    /** Clusters one contiguous same-folder run at a fixed [threshold], appending groups to [into]. */
    private fun clusterRun(
        photos: List<Photo>,
        embeddings: Map<PhotoId, FloatArray>,
        sharpness: Map<PhotoId, Float>,
        threshold: Float,
        into: MutableList<PhotoGroup>,
    ) {
        var runStart = 0
        for (i in 1..photos.size) {
            val breakHere = i == photos.size ||
                !sameShot(photos[i - 1], photos[i], embeddings, threshold)
            if (breakHere) {
                val run = photos.subList(runStart, i)
                into += if (run.size >= 2) {
                    // The key frame is the sharpest, so the collapsed tile's cover is the suggested
                    // keeper (a time burst has no quality signal and keeps the neutral middle frame).
                    PhotoGroup.Burst(run.toList(), keyIndex = sharpestIndex(run, sharpness))
                } else {
                    PhotoGroup.Single(run[0])
                }
                runStart = i
            }
        }
    }

    /** Cosine of each adjacent pair within [photos] that has two equal-length embeddings. */
    private fun adjacentCosines(photos: List<Photo>, embeddings: Map<PhotoId, FloatArray>): List<Float> {
        if (photos.size < 2) return emptyList()
        val out = ArrayList<Float>(photos.size - 1)
        for (i in 1 until photos.size) {
            val a = embeddings[photos[i - 1].id] ?: continue
            val b = embeddings[photos[i].id] ?: continue
            if (a.size == b.size) out += cosine(a, b)
        }
        return out
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

    /** Lower median of [values]; callers guarantee it is non-empty. */
    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    /** Dot product; equals cosine similarity for L2-normalized inputs. */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
