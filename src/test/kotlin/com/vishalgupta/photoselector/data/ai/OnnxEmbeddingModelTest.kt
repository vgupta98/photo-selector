package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the *real* bundled MobileNetV3-Small ONNX model through ONNX Runtime — this is the
 * integration test that proves the shipped blob loads, runs, and produces sane embeddings. It runs
 * off the classpath resource, so it also guards against the model being dropped from packaging.
 */
class OnnxEmbeddingModelTest {

    private val model = OnnxEmbeddingModel.Loader.fromResource()

    @AfterTest
    fun tearDown() = model.close()

    @Test
    fun loads_withAPositiveEmbeddingWidth() {
        assertTrue(model.dimensions > 0, "expected a positive embedding width, got ${model.dimensions}")
    }

    @Test
    fun embed_returnsAFiniteUnitVectorOfTheModelWidth() {
        val vec = model.embed(ImageFixtures.ramp(96, 96))

        assertEquals(model.dimensions, vec.size)
        assertTrue(vec.all { it.isFinite() }, "embedding had non-finite components")
        assertTrue(abs(norm(vec) - 1f) < 1e-3f, "expected an L2-normalized vector, norm was ${norm(vec)}")
    }

    @Test
    fun embed_isDeterministic_soIdenticalImagesGroup() {
        val a = model.embed(ImageFixtures.ramp(96, 96))
        val b = model.embed(ImageFixtures.ramp(96, 96))

        assertTrue(cosine(a, b) > 0.999f, "identical images should embed to (almost) the same vector")
    }

    @Test
    fun embed_separatesVisuallyDifferentImages() {
        val ramp = model.embed(ImageFixtures.ramp(96, 96))
        val checker = model.embed(ImageFixtures.checker(96, 96))

        // A learned backbone should put a smooth ramp and a high-frequency checkerboard clearly
        // apart — well under the grouper's 0.85 merge threshold.
        assertTrue(cosine(ramp, checker) < 0.85f, "distinct images should be distinguishable, got ${cosine(ramp, checker)}")
    }

    private fun norm(v: FloatArray): Float {
        var s = 0f
        for (x in v) s += x * x
        return sqrt(s)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}
