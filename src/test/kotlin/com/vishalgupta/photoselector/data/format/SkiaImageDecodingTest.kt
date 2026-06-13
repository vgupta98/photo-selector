package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SkiaImageDecodingTest {

    @Test fun `scaleUpToLongEdge scales a small image up to the target long edge`() {
        val out = SkiaImageDecoding.scaleUpToLongEdge(ImageFixtures.solid(8, 6), targetLongEdgePx = 64)
        assertEquals(64, out.width, "long edge scaled to target")
        assertEquals(48, out.height, "aspect ratio preserved")
        assertEquals(64 * 48 * 4, out.bgraBytes.size)
    }

    @Test fun `scaleUpToLongEdge leaves an already-large-enough image untouched`() {
        val src = ImageFixtures.solid(100, 80)
        // Only upscales; a frame at or above the target (here, larger than it) is returned as-is —
        // downscaling is the decoder's job, not this helper's.
        assertSame(src, SkiaImageDecoding.scaleUpToLongEdge(src, targetLongEdgePx = 64))
    }
}
