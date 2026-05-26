package com.vishalgupta.photoselector.perf

import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.JpegDecoder
import com.vishalgupta.photoselector.data.format.PngDecoder
import com.vishalgupta.photoselector.data.image.DiskThumbnailCache
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Compares thumbnail load latency with and without the disk cache.
 *
 * - **diskCacheHit** — the thumbnail is already on disk from a prior session.
 *   Measures JPEG read + decode from the cache file.
 * - **coldDecode** — no disk cache, full source decode through the pipeline.
 *
 * Both request a 320 px viewport (the favourites grid size), hitting the
 * embedded-thumb fast path when available. The in-memory cache is evicted
 * before every invocation so we isolate the disk-vs-source difference.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
@State(Scope.Benchmark)
open class DiskCacheBenchmark {

    private lateinit var loaderWithDisk: SkikoImageLoader
    private lateinit var loaderNoDisk: SkikoImageLoader
    private lateinit var jpegPath: Path
    private lateinit var cacheDir: Path
    private lateinit var photo: Photo

    @Setup(Level.Trial)
    fun setup() {
        val registry = DefaultPhotoFormatRegistry(decoders = listOf(JpegDecoder(), PngDecoder()))

        cacheDir = Files.createTempDirectory("jmh-disk-cache")
        val diskCache = DiskThumbnailCache(cacheDir = cacheDir)

        loaderWithDisk = SkikoImageLoader(
            registry = registry,
            decodeDispatcher = Dispatchers.IO,
            diskCache = diskCache,
        )
        loaderNoDisk = SkikoImageLoader(
            registry = registry,
            decodeDispatcher = Dispatchers.IO,
        )

        jpegPath = Files.createTempFile("disk-cache-bench", ".jpg")
        Files.write(jpegPath, JpegFixture.bandJpeg(4000, 3000))
        photo = Photo(
            id = PhotoId("bench"),
            absolutePath = jpegPath,
            relativePath = jpegPath.fileName.toString(),
            fileName = jpegPath.fileName.toString(),
            sizeBytes = Files.size(jpegPath),
            lastModifiedEpochMs = Files.getLastModifiedTime(jpegPath).toMillis(),
        )

        // Prime the disk cache with one load
        runBlocking { loaderWithDisk.load(photo, viewportLongEdgePx = 320) }
        loaderWithDisk.evictAll()
    }

    @TearDown(Level.Trial)
    fun teardown() {
        Files.deleteIfExists(jpegPath)
        cacheDir.toFile().deleteRecursively()
    }

    @Setup(Level.Invocation)
    fun evict() {
        loaderWithDisk.evictAll()
        loaderNoDisk.evictAll()
    }

    @Benchmark
    fun diskCacheHit() = runBlocking {
        loaderWithDisk.load(photo, viewportLongEdgePx = 320)
    }

    @Benchmark
    fun coldDecode() = runBlocking {
        loaderNoDisk.load(photo, viewportLongEdgePx = 320)
    }
}
