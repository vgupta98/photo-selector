package com.vishalgupta.photoselector.data.image

import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiskThumbnailCacheTest {

    private fun tempCache(maxBytes: Long = DiskThumbnailCache.DEFAULT_MAX_BYTES): Pair<DiskThumbnailCache, Path> {
        val dir = Files.createTempDirectory("disk-thumb-cache-test")
        dir.toFile().deleteOnExit()
        return DiskThumbnailCache(cacheDir = dir, maxBytes = maxBytes) to dir
    }

    private fun photo(name: String, sizeBytes: Long = 1000L, mtime: Long = 1000L) = Photo(
        id = PhotoId(name),
        absolutePath = Path.of("/photos/$name.jpg"),
        relativePath = "$name.jpg",
        fileName = "$name.jpg",
        sizeBytes = sizeBytes,
        lastModifiedEpochMs = mtime,
    )

    private fun solidImage(width: Int, height: Int, fill: Byte = 0x7F.toByte()): DecodedImage {
        val bytes = ByteArray(width * height * 4) { fill }
        return DecodedImage(width, height, bytes)
    }

    @Test
    fun `roundtrip stores and retrieves a thumbnail`() {
        val (cache, _) = tempCache()
        val p = photo("test")
        val original = solidImage(320, 240)

        assertNull(cache.get(p, 320))

        cache.put(p, 320, original)
        val retrieved = cache.get(p, 320)

        assertNotNull(retrieved)
        assertEquals(original.width, retrieved.width)
        assertEquals(original.height, retrieved.height)
    }

    @Test
    fun `different target sizes have independent entries`() {
        val (cache, _) = tempCache()
        val p = photo("test")

        cache.put(p, 160, solidImage(160, 120))
        cache.put(p, 320, solidImage(320, 240))

        val small = cache.get(p, 160)
        val large = cache.get(p, 320)
        assertNotNull(small)
        assertNotNull(large)
        assertEquals(160, small.width)
        assertEquals(320, large.width)
    }

    @Test
    fun `cache miss when source file metadata changes`() {
        val (cache, _) = tempCache()
        val p1 = photo("test", sizeBytes = 1000, mtime = 1000)
        cache.put(p1, 320, solidImage(320, 240))

        assertNotNull(cache.get(p1, 320))

        val p2 = photo("test", sizeBytes = 1000, mtime = 2000)
        assertNull(cache.get(p2, 320))

        val p3 = photo("test", sizeBytes = 2000, mtime = 1000)
        assertNull(cache.get(p3, 320))
    }

    @Test
    fun `cache miss for unknown photo`() {
        val (cache, _) = tempCache()
        assertNull(cache.get(photo("unknown"), 320))
    }
}
