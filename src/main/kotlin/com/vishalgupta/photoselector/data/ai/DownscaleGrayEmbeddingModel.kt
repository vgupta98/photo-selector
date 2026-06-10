package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * A dependency-free, on-device [EmbeddingModel]: box-downscale the grayscale thumbnail to an
 * [edge] x [edge] grid, then mean-subtract and L2-normalize the result. This is a perceptual
 * fingerprint in the spirit of an average/difference hash, but kept as a real float vector so the
 * cosine distance varies continuously (a tunable similarity threshold, not a hard hash collision).
 *
 * Mean-subtraction makes it tolerant of overall brightness shifts between frames; L2-normalization
 * makes cosine similarity a plain dot product. It is intentionally simple and explainable: good
 * enough to cluster near-identical frames today, and a clean stand-in until a learned ONNX model
 * is swapped in behind [EmbeddingModel] for stronger "same scene, different moment" recall.
 */
class DownscaleGrayEmbeddingModel(
    private val edge: Int = DEFAULT_EDGE,
) : EmbeddingModel {

    init {
        require(edge >= 2) { "edge must be at least 2, got $edge" }
    }

    override val id: String = "downscale-gray-${edge}x$edge-v1"
    override val dimensions: Int = edge * edge

    override fun embed(image: DecodedImage): FloatArray {
        val gray = image.toGrayBuffer()
        val w = image.width
        val h = image.height
        val vec = FloatArray(dimensions)

        // Box-average each source region into its output cell. Integer edge mapping keeps the
        // partition exact and gap-free even when w/h aren't multiples of [edge].
        for (oy in 0 until edge) {
            val y0 = oy * h / edge
            val y1 = ((oy + 1) * h / edge).coerceAtLeast(y0 + 1).coerceAtMost(h)
            for (ox in 0 until edge) {
                val x0 = ox * w / edge
                val x1 = ((ox + 1) * w / edge).coerceAtLeast(x0 + 1).coerceAtMost(w)
                var sum = 0f
                var n = 0
                for (y in y0 until y1) {
                    val row = y * w
                    for (x in x0 until x1) {
                        sum += gray[row + x]
                        n++
                    }
                }
                vec[oy * edge + ox] = if (n == 0) 0f else sum / n
            }
        }

        return normalize(vec)
    }

    /** Mean-subtract then L2-normalize in place; a flat (zero-variance) image yields the zero vector. */
    private fun normalize(vec: FloatArray): FloatArray {
        var mean = 0f
        for (v in vec) mean += v
        mean /= vec.size
        var norm = 0f
        for (i in vec.indices) {
            val centered = vec[i] - mean
            vec[i] = centered
            norm += centered * centered
        }
        norm = kotlin.math.sqrt(norm)
        if (norm > 1e-6f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec
    }

    companion object {
        const val DEFAULT_EDGE = 16
    }
}
