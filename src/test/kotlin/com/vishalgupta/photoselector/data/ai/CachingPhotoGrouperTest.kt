package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.grouping.GroupingProgress
import com.vishalgupta.photoselector.domain.grouping.PhotoGrouper
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachingPhotoGrouperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val photos = listOf("a", "b", "c", "d").map { name ->
        Photo(
            id = PhotoId(name),
            absolutePath = Path.of("/photos/$name.jpg"),
            relativePath = "$name.jpg",
            fileName = "$name.jpg",
            sizeBytes = 1000,
            lastModifiedEpochMs = 1000,
        )
    }

    // a | [b c] burst | d
    private val groups = listOf(
        PhotoGroup.Single(photos[0]),
        PhotoGroup.Burst(photos.subList(1, 3), keyIndex = 0, keyIsSuggested = true),
        PhotoGroup.Single(photos[3]),
    )

    private fun cache(): GroupingResultCache {
        val dir = Files.createTempDirectory("caching-grouper-test").also { it.toFile().deleteOnExit() }
        return GroupingResultCache(cacheDir = dir, json = json)
    }

    private class CountingGrouper(val result: List<PhotoGroup>) : PhotoGrouper {
        var calls = 0
        override suspend fun group(photos: List<Photo>, onProgress: GroupingProgress): List<PhotoGroup> {
            calls++
            onProgress(photos.size, photos.size)
            return result
        }
    }

    @Test fun `second pass over an unchanged set hits the cache without re-running the delegate`() = runBlocking {
        val delegate = CountingGrouper(groups)
        val grouper = CachingPhotoGrouper(delegate, cache(), modelId = "model-a")

        val first = grouper.group(photos)
        assertEquals(1, delegate.calls)
        assertEquals(3, first.size)

        // A fresh decorator over the SAME cache dir would also hit; here the same instance suffices.
        val second = grouper.group(photos)
        assertEquals(1, delegate.calls, "the second pass must not re-run the model")
        assertEquals(groups.map { it.groupId }, second.map { it.groupId })
        val burst = second[1] as PhotoGroup.Burst
        assertTrue(burst.keyIsSuggested)
    }

    @Test fun `an incomplete pass that throws is never cached`() = runBlocking {
        // A pass the grid cancels mid-flight throws out of the delegate before the decorator can write,
        // so a partial/absent grouping is never persisted (a later pass recomputes from scratch).
        val cancelled = object : PhotoGrouper {
            override suspend fun group(photos: List<Photo>, onProgress: GroupingProgress): List<PhotoGroup> =
                throw CancellationException("cancelled mid-pass")
        }
        val store = cache()
        val grouper = CachingPhotoGrouper(cancelled, store, modelId = "model-a")

        try {
            grouper.group(photos)
        } catch (_: CancellationException) {
            // expected
        }
        assertNull(store.get(store.keyFor("model-a", photos), photos))
    }
}
