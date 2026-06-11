package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.testing.FakeEmbeddingModel
import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Exercises the data-layer half end to end: extract features (decode -> embed -> cache) and feed
 * the pure grouper. The fake decode tags each photo with a colour and the fake model maps that
 * colour to a vector, so the test controls exactly which frames look alike — and can count decodes
 * to prove the cache is doing its job.
 */
class SimilarityPhotoGrouperTest {

    @Test fun `groups visually-alike photos and reuses cached features on the second pass`() = runBlocking {
        val dir = Files.createTempDirectory("similarity-grouper-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")

        // A and B share a colour (one cluster); C is different (a single).
        val a = photo("A"); val b = photo("B"); val c = photo("C")
        val colour = mapOf(a.id to 10, b.id to 10, c.id to 200)

        var decodeCount = 0
        val decode: suspend (Photo) -> com.vishalgupta.photoselector.domain.model.DecodedImage? = { photo ->
            decodeCount++
            ImageFixtures.solid(8, 8, r = colour.getValue(photo.id))
        }
        // Map the decoded colour to one of two orthogonal vectors.
        val model = FakeEmbeddingModel(id = "fake-v1") { image ->
            val red = image.bgraBytes[2].toInt() and 0xFF
            if (red < 128) unit(1f, 0f) else unit(0f, 1f)
        }
        val grouper = SimilarityPhotoGrouper(PhotoFeatureExtractor(model, cache, decode))

        val first = grouper.group(listOf(a, b, c))
        assertEquals(listOf(a, b), assertIs<PhotoGroup.Burst>(first[0]).photos)
        assertIs<PhotoGroup.Single>(first[1])
        assertEquals(3, decodeCount, "first pass decodes each photo once")

        val second = grouper.group(listOf(a, b, c))
        assertEquals(listOf(a, b), assertIs<PhotoGroup.Burst>(second[0]).photos)
        assertEquals(3, decodeCount, "second pass is all cache hits, no re-decode")
    }

    @Test fun `reports progress once per photo, ending at total`() = runBlocking {
        val dir = Files.createTempDirectory("similarity-progress-test").also { it.toFile().deleteOnExit() }
        val cache = EmbeddingCache(dir, modelId = "fake-v1")
        val a = photo("A"); val b = photo("B"); val c = photo("C")
        val decode: suspend (Photo) -> com.vishalgupta.photoselector.domain.model.DecodedImage? =
            { ImageFixtures.solid(8, 8) }
        val model = FakeEmbeddingModel(id = "fake-v1") { unit(1f, 0f) }
        val grouper = SimilarityPhotoGrouper(PhotoFeatureExtractor(model, cache, decode))

        val seen = mutableListOf<Pair<Int, Int>>()
        grouper.group(listOf(a, b, c)) { processed, total -> seen += processed to total }

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), seen)
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
