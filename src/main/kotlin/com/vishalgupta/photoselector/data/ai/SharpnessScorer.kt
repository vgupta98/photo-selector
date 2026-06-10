package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * A classical, explainable focus metric: the **variance of the Laplacian** of the grayscale image.
 * The Laplacian is a second-derivative edge response; a sharp frame has strong, high-variance edges
 * while a soft/blurred one washes them out, so a higher score means a crisper frame.
 *
 * Deliberately *not* a learned aesthetic/quality model — taste is subjective and photographers
 * reject it (see the similarity-grouping brief). This is only a hint to suggest which frame in a
 * cluster of near-identical shots is sharpest; motion blur and shallow depth of field are often
 * intentional, so the number speeds a human pass, it never renders a verdict.
 */
object SharpnessScorer {

    /** Variance of the 3x3 Laplacian over [image]'s interior pixels. 0 for images too small to convolve. */
    fun score(image: DecodedImage): Float {
        val w = image.width
        val h = image.height
        if (w < 3 || h < 3) return 0f
        val gray = image.toGrayBuffer()

        // Online mean/variance (Welford) over the Laplacian response of every interior pixel.
        var count = 0
        var mean = 0.0
        var m2 = 0.0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = 4f * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]
                count++
                val delta = lap - mean
                mean += delta / count
                m2 += delta * (lap - mean)
            }
        }
        return if (count == 0) 0f else (m2 / count).toFloat()
    }
}
