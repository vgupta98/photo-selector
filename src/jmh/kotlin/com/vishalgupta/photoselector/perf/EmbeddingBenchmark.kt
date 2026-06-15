package com.vishalgupta.photoselector.perf

import com.vishalgupta.photoselector.data.ai.OnnxEmbeddingModel
import com.vishalgupta.photoselector.domain.model.DecodedImage
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
import java.util.concurrent.TimeUnit

/**
 * Measures the per-photo cost of the learned similarity embedder — the dominant work in a cold
 * Similarity grouping pass, and the number that sets its minute(s)-long wait on a large library.
 *
 * - **embed** — one decoded frame through `OnnxEmbeddingModel`: the 224x224 bilinear preprocess plus
 *   a MobileNetV3-Small forward pass via ONNX Runtime. Decode is deliberately **excluded** (it has
 *   its own `DecodeBenchmark`), so the real per-photo pass cost is roughly
 *   `DecodeBenchmark.* + EmbeddingBenchmark.embed`. Multiply `embed` by the library size to estimate
 *   the one-time cold wait (50k photos x this).
 *
 * Runs the *real* bundled model off the classpath (the same blob the app ships), so it also guards
 * against the model being dropped from packaging. Absolute numbers are hardware-specific; only the
 * cross-branch delta is meaningful (the release-perf CI diff), so a regression here — a heavier model,
 * a costlier preprocess — shows up as a delta on the release PR.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
@State(Scope.Benchmark)
open class EmbeddingBenchmark {

    private lateinit var model: OnnxEmbeddingModel
    private lateinit var image: DecodedImage

    @Setup(Level.Trial)
    fun setup() {
        model = OnnxEmbeddingModel.Loader.fromResource()
        // A representative decoded frame. The model squashes any input to a fixed 224x224 tensor, so
        // inference cost is independent of these dimensions — the bytes only need to be non-trivial so
        // nothing degenerates to a constant. Mirrors the ~224px frame the production pass decodes to.
        image = syntheticBgra(width = 256, height = 256)
    }

    @TearDown(Level.Trial)
    fun teardown() {
        model.close()
    }

    @Benchmark
    fun embed(): FloatArray = model.embed(image)

    /** A non-uniform BGRA buffer (a per-channel gradient) so preprocess/inference see real variation. */
    private fun syntheticBgra(width: Int, height: Int): DecodedImage {
        val bytes = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[i] = (x and 0xFF).toByte()             // B
                bytes[i + 1] = (y and 0xFF).toByte()         // G
                bytes[i + 2] = ((x + y) and 0xFF).toByte()   // R
                bytes[i + 3] = 0xFF.toByte()                 // A (opaque)
                i += 4
            }
        }
        return DecodedImage(width, height, bytes)
    }
}
