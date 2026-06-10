package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownscaleGrayEmbeddingModelTest {

    private val model = DownscaleGrayEmbeddingModel(edge = 8)

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    @Test fun `vector length is edge squared`() {
        assertEquals(64, model.dimensions)
        assertEquals(64, model.embed(ImageFixtures.ramp(40, 40)).size)
    }

    @Test fun `identical images embed to cosine one`() {
        val a = model.embed(ImageFixtures.ramp(40, 40, horizontal = true))
        val b = model.embed(ImageFixtures.ramp(40, 40, horizontal = true))
        assertEquals(1f, cosine(a, b), 1e-4f)
    }

    @Test fun `a horizontal and a vertical ramp are clearly less similar than identical ones`() {
        val horizontal = model.embed(ImageFixtures.ramp(40, 40, horizontal = true))
        val vertical = model.embed(ImageFixtures.ramp(40, 40, horizontal = false))
        assertTrue(cosine(horizontal, vertical) < 0.5f, "orthogonal structure should not look identical")
    }

    @Test fun `a flat image yields the zero vector`() {
        val flat = model.embed(ImageFixtures.solid(40, 40))
        assertTrue(flat.all { it == 0f }, "a contrast-free image has no fingerprint")
    }
}
