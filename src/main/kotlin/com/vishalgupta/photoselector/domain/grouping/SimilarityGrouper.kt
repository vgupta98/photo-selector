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
 *  - the [JoinRule] accepts the pair given their cosine similarity, the run's cut (see [ThresholdRule])
 *    and the capture-time gap between them.
 *
 * Capture time is not *required* — two near-identical shots from different cameras still group on
 * visual similarity alone, which is the point of the visual lens. But it is used as **corroborating
 * evidence**: the shipped [timeBoosted] rule additionally joins frames captured within a few seconds
 * of each other (the same moment) even when the embedding drifts below the cut — a framing or zoom
 * shift mid-burst that a purely visual cut would wrongly split. A frame with no capture time (HEIC,
 * EXIF-less) simply falls back to the visual cut, so time can only ever *add* joins, never block one.
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
 *
 * ## Time as corroborating evidence
 * The cosine cut alone misses same-moment frames whose embedding drifted (a big framing/zoom shift):
 * on the labelled set, of the real adjacent pairs the visual cut missed, many were captured ~3s apart.
 * The shipped [timeBoosted] [JoinRule] recovers them by relaxing the cosine bar for frames within a
 * few seconds, lifting recall. The window is deliberately tight (3s): a wider one over-merges "clean"
 * events the cosine already handles (shown to regress them in leave-one-event-out validation), so the
 * boost only fires on genuine rapid bursts.
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

    /**
     * Constant cut, ignoring the distribution — the original behaviour. No production caller uses it;
     * its only consumers are the out-of-tree `EVAL_ROOT` threshold sweep and the mechanics unit tests.
     */
    fun fixed(threshold: Float = DEFAULT_THRESHOLD): ThresholdRule = ThresholdRule { threshold }

    /**
     * Per-run adaptive cut: a pair joins iff it is at least [ADAPTIVE_MARGIN] more similar than the
     * run's **median** adjacent-pair cosine — a burst stands out above the folder's typical neighbour
     * — clamped to [[MIN_THRESHOLD], [MAX_THRESHOLD]]. An empty run (no comparable pairs) cuts at
     * [MAX_THRESHOLD], i.e. groups nothing. This is the shipped default; see the class KDoc for why.
     *
     * Limitation: relative thresholding assumes the run contains a *mix* of burst and non-burst
     * neighbours to set its baseline. A folder that is one uniform burst (every adjacent pair similar,
     * e.g. ~0.90) has a high median, so the cut rides up toward [MAX_THRESHOLD] and the burst can
     * shatter into singles. A run mixing a burst with ordinary frames is unaffected. For time-stamped
     * runs [timeBoosted] backstops this — a rapid uniform burst rejoins on the capture-time gap despite
     * the inflated cut — so the residual is only frames with no capture time (a stripped JPEG, or HEIC
     * off macOS) and uniform sequences slower than the boost window.
     */
    val Adaptive: ThresholdRule = ThresholdRule { cosines ->
        if (cosines.isEmpty()) MAX_THRESHOLD
        else (median(cosines) + ADAPTIVE_MARGIN).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    /** Capture-time boost defaults, validated leave-one-event-out (see the class KDoc). */
    const val DEFAULT_BOOST_WITHIN_MS = 3_000L
    const val DEFAULT_BOOST_FLOOR = 0.65f

    /**
     * Decides whether two adjacent same-folder frames are the same shot, given their [cosine]
     * similarity, the capture-time gap in ms ([timeGapMs], null when either time is unknown) and the
     * run's adaptive cosine [cut]. The default [VisualOnly] is purely visual; a time-aware rule can
     * also relax the bar for frames captured close together. This is the seam a future learned
     * "same-moment?" judge would slot into.
     */
    fun interface JoinRule {
        fun join(cosine: Float, timeGapMs: Long?, cut: Float): Boolean
    }

    /** Purely visual: join iff the cosine clears the run's cut. The original behaviour. */
    val VisualOnly: JoinRule = JoinRule { cosine, _, cut -> cosine >= cut }

    /**
     * Visual OR a capture-time boost: two frames taken within [boostWithinMs] also join at a relaxed
     * [boostFloor] cosine, because frames seconds apart are almost always the same moment even when the
     * embedding drifts. A null gap (no capture time, e.g. a stripped JPEG or off-macOS HEIC) falls
     * back to visual-only, so the boost
     * can never *block* a visual join — only add one. Keep the window tight; see the class KDoc. As a
     * side effect this also backstops [Adaptive]'s uniform-burst blind spot for time-stamped runs.
     */
    fun timeBoosted(
        boostWithinMs: Long = DEFAULT_BOOST_WITHIN_MS,
        boostFloor: Float = DEFAULT_BOOST_FLOOR,
    ): JoinRule = JoinRule { cosine, gap, cut ->
        cosine >= cut || (gap != null && gap <= boostWithinMs && cosine >= boostFloor)
    }

    fun group(
        photos: List<Photo>,
        embeddings: Map<PhotoId, FloatArray>,
        sharpness: Map<PhotoId, Float> = emptyMap(),
        captureTimesMs: Map<PhotoId, Long> = emptyMap(),
        rule: ThresholdRule = Adaptive,
        joinRule: JoinRule = VisualOnly,
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
            clusterRun(folderRun, embeddings, sharpness, captureTimesMs, threshold, joinRule, into = groups)
            runStart = runEnd
        }
        return groups
    }

    /** Clusters one contiguous same-folder run at a fixed [threshold], appending groups to [into]. */
    private fun clusterRun(
        photos: List<Photo>,
        embeddings: Map<PhotoId, FloatArray>,
        sharpness: Map<PhotoId, Float>,
        captureTimesMs: Map<PhotoId, Long>,
        threshold: Float,
        joinRule: JoinRule,
        into: MutableList<PhotoGroup>,
    ) {
        var runStart = 0
        for (i in 1..photos.size) {
            val breakHere = i == photos.size ||
                !sameShot(photos[i - 1], photos[i], embeddings, captureTimesMs, threshold, joinRule)
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
        captureTimesMs: Map<PhotoId, Long>,
        threshold: Float,
        joinRule: JoinRule,
    ): Boolean {
        if (a.absolutePath.parent != b.absolutePath.parent) return false
        val embA = embeddings[a.id] ?: return false
        val embB = embeddings[b.id] ?: return false
        if (embA.size != embB.size) return false
        val ta = captureTimesMs[a.id]
        val tb = captureTimesMs[b.id]
        val gap = if (ta != null && tb != null) kotlin.math.abs(ta - tb) else null
        return joinRule.join(cosine(embA, embB), gap, threshold)
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
