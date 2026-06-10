package com.vishalgupta.photoselector.testing

import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * Synthetic [DecodedImage]s for AI/decode tests — deterministic BGRA8888 buffers built in memory,
 * so tests never touch Skia-encoded fixtures (which carry their own orientation/colour quirks).
 * Layout matches `DecodedImage`: 4 bytes per pixel in B, G, R, A order, row-major, alpha opaque.
 */
object ImageFixtures {

    /** A flat, single-colour image. Low sharpness (no edges) and, after mean-subtraction, a zero embedding. */
    fun solid(width: Int, height: Int, r: Int = 128, g: Int = 128, b: Int = 128): DecodedImage =
        build(width, height) { _, _ -> Triple(r, g, b) }

    /** An intensity ramp across the image — non-flat, so it produces a meaningful embedding. */
    fun ramp(width: Int, height: Int, horizontal: Boolean = true): DecodedImage =
        build(width, height) { x, y ->
            val t = if (horizontal) x else y
            val span = (if (horizontal) width else height).coerceAtLeast(1)
            val v = t * 255 / span
            Triple(v, v, v)
        }

    /** A 1px black/white checkerboard — maximal high-frequency edges, so it scores very sharp. */
    fun checker(width: Int, height: Int): DecodedImage =
        build(width, height) { x, y -> if ((x + y) % 2 == 0) Triple(255, 255, 255) else Triple(0, 0, 0) }

    private fun build(width: Int, height: Int, pixel: (x: Int, y: Int) -> Triple<Int, Int, Int>): DecodedImage {
        val bytes = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (r, g, b) = pixel(x, y)
                bytes[i] = b.toByte()
                bytes[i + 1] = g.toByte()
                bytes[i + 2] = r.toByte()
                bytes[i + 3] = 255.toByte()
                i += 4
            }
        }
        return DecodedImage(width = width, height = height, bgraBytes = bytes)
    }
}
