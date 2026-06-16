package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.testing.FakeEmbeddingModel
import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the data-layer half end to end: extract features (decode -> embed -> cache) and feed
 * the pure grouper. The fake decode tags each photo with a colour and the fake model maps that
 * colour to a vector, so the test controls exactly which frames look alike — and can count decodes
 * to prove the cache is doing its job.
 */
class SimilarityPhotoGrouperTest {

    @Test fun `groups visually-alike photos and reuses cached features on the second pass`() = runTest {
        val dir = Files.createTempDirectory("similarity-grouper-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")

        // A and B share a colour (one cluster); C is different (a single).
        val a = photo("A"); val b = photo("B"); val c = photo("C")
        val colour = mapOf(a.id to 10, b.id to 10, c.id to 200)

        var decodeCount = 0
        val decode: suspend (Photo) -> DecodedImage? = { photo ->
            decodeCount++
            ImageFixtures.solid(8, 8, r = colour.getValue(photo.id))
        }
        // Map the decoded colour to one of two orthogonal vectors.
        val model = FakeEmbeddingModel(id = "fake-v1") { image ->
            val red = image.bgraBytes[2].toInt() and 0xFF
            if (red < 128) unit(1f, 0f) else unit(0f, 1f)
        }
        // The same decode backs both lambdas here, so each photo decodes twice on a miss.
        val grouper = SimilarityPhotoGrouper(PhotoFeatureExtractor(model, cache, decode, decode))

        val first = grouper.group(listOf(a, b, c))
        assertEquals(listOf(a, b), assertIs<PhotoGroup.Burst>(first[0]).photos)
        assertIs<PhotoGroup.Single>(first[1])
        assertEquals(6, decodeCount, "first pass decodes each photo twice: embedding + sharpness")

        val second = grouper.group(listOf(a, b, c))
        assertEquals(listOf(a, b), assertIs<PhotoGroup.Burst>(second[0]).photos)
        assertEquals(6, decodeCount, "second pass is all cache hits, no re-decode")
    }

    @Test fun `reports progress once per photo with a thread-safe running count, ending at total`() = runTest {
        val dir = Files.createTempDirectory("similarity-progress-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")
        val a = photo("A"); val b = photo("B"); val c = photo("C")
        val decode: suspend (Photo) -> DecodedImage? = { ImageFixtures.solid(8, 8) }
        val model = FakeEmbeddingModel(id = "fake-v1") { unit(1f, 0f) }
        val grouper = SimilarityPhotoGrouper(PhotoFeatureExtractor(model, cache, decode, decode))

        // Extraction is parallel, so callbacks arrive out of order — but the count is a thread-safe
        // AtomicInteger, so every photo is reported exactly once (processed values are 1..total) and
        // the run always lands on total. The caller (GridViewModel) is what makes the bar monotonic.
        val seen = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
        grouper.group(listOf(a, b, c)) { processed, total -> seen += processed to total }

        assertTrue(seen.all { it.second == 3 }, "every callback reports the same total")
        assertEquals(listOf(1, 2, 3), seen.map { it.first }.sorted(), "each photo counted once, no gaps or repeats")
    }

    @Test fun `a cancelled pass propagates cancellation and never produces groups`() = runTest {
        val dir = Files.createTempDirectory("similarity-cancel-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")
        val a = photo("A"); val b = photo("B"); val c = photo("C")
        // The first decode to run parks forever, so the fan-out is genuinely in-flight when we cancel.
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val decode: suspend (Photo) -> DecodedImage? = {
            started.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }
        val model = FakeEmbeddingModel(id = "fake-v1") { unit(1f, 0f) }
        val grouper = SimilarityPhotoGrouper(PhotoFeatureExtractor(model, cache, decode, decode), concurrency = 2)

        val pass = async { grouper.group(listOf(a, b, c)) }
        started.await()
        pass.cancelAndJoin()

        assertTrue(pass.isCancelled, "cancellation must propagate structurally through the fan-out")
    }

    @Test fun `sharpness is scored from the dedicated sharpness decode, not the embedding decode`() = runTest {
        val dir = Files.createTempDirectory("sharpness-decode-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")
        val p = photo("A")
        // Embedding decode: a flat fill (Laplacian variance 0). Sharpness decode: a high-frequency
        // checker (high variance). Were sharpness read off the embedding image it would be 0.
        val embedDecode: suspend (Photo) -> DecodedImage? = { ImageFixtures.solid(16, 16) }
        val sharpDecode: suspend (Photo) -> DecodedImage? = { ImageFixtures.checker(16, 16) }
        val model = FakeEmbeddingModel(id = "fake-v1") { unit(1f, 0f) }
        val extractor = PhotoFeatureExtractor(model, cache, embedDecode, sharpDecode)

        val features = assertNotNull(extractor.featuresFor(p))
        assertTrue(
            features.sharpness > 0f,
            "sharpness must come from the checker sharpness-decode, not the flat embedding decode",
        )
    }

    @Test fun `a failed sharpness decode leaves the frame unassessable, scoring zero`() = runTest {
        val dir = Files.createTempDirectory("sharpness-fallback-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")
        val p = photo("A")
        // No sharpness decode: the frame must NOT be scored on the embedding's smaller canvas (that
        // would be incomparable with siblings scored at the canonical size). Unassessable -> 0, so it
        // simply can't win the key-frame pick. The embedding still succeeds from its own decode.
        val embedDecode: suspend (Photo) -> DecodedImage? = { ImageFixtures.checker(16, 16) }
        val sharpDecode: suspend (Photo) -> DecodedImage? = { null }
        val model = FakeEmbeddingModel(id = "fake-v1") { unit(1f, 0f) }
        val extractor = PhotoFeatureExtractor(model, cache, embedDecode, sharpDecode)

        val features = assertNotNull(extractor.featuresFor(p))
        assertEquals(0f, features.sharpness, "an unscorable frame is unassessable (0), not scored on a smaller canvas")
    }

    @Test fun `a transient embed failure is not cached and the frame groups on the next pass`() = runTest {
        val dir = Files.createTempDirectory("embed-failure-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")
        // A and B are adjacent and visually identical (same vector), so they should form one burst —
        // but B's embed fails on the first pass. Distinct decode colours only so the model can tell
        // which frame is B; both still map to the same embedding.
        val a = photo("A"); val b = photo("B")
        val colour = mapOf(a.id to 10, b.id to 11)
        val decode: suspend (Photo) -> DecodedImage? = { ImageFixtures.solid(8, 8, r = colour.getValue(it.id)) }
        var failB = true
        val model = FakeEmbeddingModel(id = "fake-v1") { image ->
            val isB = (image.bgraBytes[2].toInt() and 0xFF) == 11
            if (isB && failB) null else unit(1f, 0f)
        }
        val grouper = SimilarityPhotoGrouper(PhotoFeatureExtractor(model, cache, decode, decode))

        // First pass: B failed to embed, so both frames stand alone...
        val first = grouper.group(listOf(a, b))
        assertIs<PhotoGroup.Single>(first[0])
        assertIs<PhotoGroup.Single>(first[1])
        // ...and crucially the failure was NOT persisted: A is cached, B is not.
        assertNotNull(cache.get(a), "a successful embed is cached")
        assertNull(cache.get(b), "a failed embed must not poison the cache")

        // Inference recovers; the second pass re-embeds B (no poisoned hit) and groups it with A.
        failB = false
        val second = grouper.group(listOf(a, b))
        assertEquals(listOf(a, b), assertIs<PhotoGroup.Burst>(second[0]).photos)
    }

    private fun unit(vararg xs: Float): FloatArray {
        var norm = 0f
        for (x in xs) norm += x * x
        norm = sqrt(norm)
        return FloatArray(xs.size) { xs[it] / norm }
    }

    private fun photo(id: String): Photo {
        val path = Path.of("/root/A/$id.jpg")
        return Photo(
            id = PhotoId(id),
            absolutePath = path,
            relativePath = path.toString(),
            fileName = "$id.jpg",
            sizeBytes = 0,
            lastModifiedEpochMs = 0,
        )
    }
}
