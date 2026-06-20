package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        val vec = assertNotNull(model.embed(ImageFixtures.ramp(96, 96)), "a valid image should embed")

        assertEquals(model.dimensions, vec.size)
        assertTrue(vec.all { it.isFinite() }, "embedding had non-finite components")
        assertTrue(abs(norm(vec) - 1f) < 1e-3f, "expected an L2-normalized vector, norm was ${norm(vec)}")
    }

    @Test
    fun embed_isDeterministic_soIdenticalImagesGroup() {
        val a = assertNotNull(model.embed(ImageFixtures.ramp(96, 96)))
        val b = assertNotNull(model.embed(ImageFixtures.ramp(96, 96)))

        assertTrue(cosine(a, b) > 0.999f, "identical images should embed to (almost) the same vector")
    }

    @Test
    fun embed_separatesVisuallyDifferentImages() {
        val ramp = assertNotNull(model.embed(ImageFixtures.ramp(96, 96)))
        val checker = assertNotNull(model.embed(ImageFixtures.checker(96, 96)))

        // A learned backbone should put a smooth ramp and a high-frequency checkerboard clearly
        // apart — well under the grouper's 0.85 merge threshold.
        assertTrue(cosine(ramp, checker) < 0.85f, "distinct images should be distinguishable, got ${cosine(ramp, checker)}")
    }

    @Test
    fun embed_isSafeUnderConcurrentRunsOnTheSharedSession() = runTest {
        // The cold Similarity pass (SimilarityPhotoGrouper) embeds frames in parallel against this one
        // shared OrtSession. ONNX Runtime supports concurrent Run() on a session; this is the brief's
        // load-bearing assumption, so prove it on the real native rather than trusting the doc.
        // Every concurrent embed of the same image must match the single-threaded result exactly.
        //
        // The real parallelism comes from async(Dispatchers.IO) below — those run on real OS threads
        // independently of the test runner, so runTest (not runBlocking) is correct here.
        val reference = assertNotNull(model.embed(ImageFixtures.ramp(96, 96)))

        val results = (0 until 16).map {
            async(Dispatchers.IO) { assertNotNull(model.embed(ImageFixtures.ramp(96, 96))) }
        }.awaitAll()

        for (vec in results) {
            assertEquals(reference.size, vec.size)
            assertTrue(cosine(reference, vec) > 0.999f, "concurrent embeds must match the single-threaded vector")
        }
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
