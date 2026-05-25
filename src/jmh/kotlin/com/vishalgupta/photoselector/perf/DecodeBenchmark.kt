package com.vishalgupta.photoselector.perf

import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.JpegDecoder
import com.vishalgupta.photoselector.data.format.PngDecoder
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
 * Measures end-to-end decode latency through `SkikoImageLoader.load`, covering
 * both code paths in the production decode pipeline:
 *
 * - **fullDecode** — large JPEG without an embedded thumb. Exercises the full
 *   Skia decode + BGRA→ARGB→BGRA copy + bitmap install path.
 * - **embeddedThumbFastPath** — large JPEG with an APP1-embedded thumb,
 *   requested at a small viewport so `JpegDecoder.tryDecodeEmbeddedThumbnail`
 *   wins over the full decode (added in PR #10).
 *
 * The cache is evicted before every invocation so we measure cold decode, not
 * cache hits.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
@State(Scope.Benchmark)
open class DecodeBenchmark {

    private lateinit var loader: SkikoImageLoader
    private lateinit var fullDecodePath: Path
    private lateinit var embeddedThumbPath: Path
    private lateinit var fullDecodePhoto: Photo
    private lateinit var embeddedThumbPhoto: Photo

    @Setup(Level.Trial)
    fun setup() {
        val registry = DefaultPhotoFormatRegistry(decoders = listOf(JpegDecoder(), PngDecoder()))
        loader = SkikoImageLoader(registry = registry, decodeDispatcher = Dispatchers.IO)

        fullDecodePath = writeTemp("decode-full", JpegFixture.bandJpeg(4000, 3000))
        fullDecodePhoto = asPhoto(fullDecodePath, "full")

        val outerForThumb = JpegFixture.bandJpeg(4000, 3000)
        val thumb = JpegFixture.bandJpeg(320, 240)
        val withThumb = JpegFixture.spliceApp1(
            outerForThumb,
            JpegFixture.app1WithEmbeddedThumb(thumb),
        )
        embeddedThumbPath = writeTemp("decode-thumb", withThumb)
        embeddedThumbPhoto = asPhoto(embeddedThumbPath, "thumb")
    }

    @TearDown(Level.Trial)
    fun teardown() {
        Files.deleteIfExists(fullDecodePath)
        Files.deleteIfExists(embeddedThumbPath)
    }

    @Setup(Level.Invocation)
    fun evict() {
        loader.evictAll()
    }

    @Benchmark
    fun fullDecode() = runBlocking {
        loader.load(fullDecodePhoto, viewportLongEdgePx = 1600)
    }

    @Benchmark
    fun embeddedThumbFastPath() = runBlocking {
        loader.load(embeddedThumbPhoto, viewportLongEdgePx = 320)
    }

    private fun writeTemp(prefix: String, bytes: ByteArray): Path {
        val p = Files.createTempFile(prefix, ".jpg")
        Files.write(p, bytes)
        return p
    }

    private fun asPhoto(path: Path, id: String) = Photo(
        id = PhotoId(id),
        absolutePath = path,
        relativePath = path.fileName.toString(),
        fileName = path.fileName.toString(),
        sizeBytes = Files.size(path),
        lastModifiedEpochMs = 0,
    )
}
