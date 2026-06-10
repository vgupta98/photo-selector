package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * Converts a [DecodedImage] (BGRA8888, sRGB, premultiplied; see `DecodedImage`) to a row-major
 * grayscale buffer in `[0, 1]`, one float per pixel. Shared by [SharpnessScorer] and
 * [DownscaleGrayEmbeddingModel] so the luma conversion lives in one place.
 *
 * Photos are opaque (alpha 255), so premultiplication is a no-op here; we read B, G, R directly
 * and apply Rec. 601 luma weights.
 */
internal fun DecodedImage.toGrayBuffer(): FloatArray {
    val pixels = width * height
    val gray = FloatArray(pixels)
    var b = 0
    for (i in 0 until pixels) {
        val blue = bgraBytes[b].toInt() and 0xFF
        val green = bgraBytes[b + 1].toInt() and 0xFF
        val red = bgraBytes[b + 2].toInt() and 0xFF
        gray[i] = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
        b += 4
    }
    return gray
}
