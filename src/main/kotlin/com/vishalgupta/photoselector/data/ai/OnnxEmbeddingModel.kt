package com.vishalgupta.photoselector.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.vishalgupta.photoselector.domain.model.DecodedImage
import java.nio.FloatBuffer

/**
 * A learned, on-device [EmbeddingModel]: an ImageNet-pretrained MobileNetV3-Small backbone (its
 * classifier removed, so the single graph output is the global-pooled feature vector) run through
 * ONNX Runtime. The vector's cosine distance encodes visual similarity far better than the classical
 * [DownscaleGrayEmbeddingModel] — it tolerates exposure, white-balance and small framing shifts
 * between near-identical frames — while staying small and fast enough to run over tens of thousands
 * of photos on the CPU.
 *
 * The model blob ships as a classpath resource (see `tools/embedding-model/` for the reproducible
 * export); nothing is downloaded and no pixels leave the machine. Construction loads the session and
 * probes the output width, so [dimensions] always matches the bundled graph. Inference itself never
 * throws — a runtime failure returns `null` so a single bad frame can never crash the grid, and the
 * failure is *not* cached (see [PhotoFeatureExtractor]) so a transient hiccup re-embeds next pass
 * rather than excluding the frame from grouping for the life of the file.
 *
 * Holds a native ONNX Runtime session; [close] releases it. In production it is an app-lifetime
 * singleton owned by `AppContainer`, so the OS reclaims it at exit; [close] exists for tests.
 */
class OnnxEmbeddingModel private constructor(
    override val id: String,
    override val dimensions: Int,
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val inputName: String,
) : EmbeddingModel, AutoCloseable {

    override fun embed(image: DecodedImage): FloatArray? = try {
        val input = preprocess(image)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), INPUT_SHAPE).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<FloatArray>
                normalizeL2(out[0])
            }
        }
    } catch (t: Throwable) {
        System.err.println("OnnxEmbeddingModel.embed failed, returning null (not cached): ${t.message}")
        null
    }

    /**
     * Bilinear-resizes [image] (any size, BGRA) to [EDGE] x [EDGE], reorders to RGB and applies
     * ImageNet normalization, laid out as a CHW (channel-major) float buffer — the layout
     * MobileNetV3 was trained on. Aspect ratio is squashed to the square input; that is consistent
     * across frames, which is all the similarity comparison needs.
     */
    private fun preprocess(image: DecodedImage): FloatArray {
        val w = image.width
        val h = image.height
        val src = image.bgraBytes
        val out = FloatArray(3 * EDGE * EDGE)
        val plane = EDGE * EDGE

        for (oy in 0 until EDGE) {
            // Pixel-centre mapping (OpenCV/PIL convention), clamped to the source bounds.
            val sy = ((oy + 0.5f) * h / EDGE - 0.5f).coerceIn(0f, (h - 1).toFloat())
            val y0 = sy.toInt()
            val y1 = (y0 + 1).coerceAtMost(h - 1)
            val wy = sy - y0
            for (ox in 0 until EDGE) {
                val sx = ((ox + 0.5f) * w / EDGE - 0.5f).coerceIn(0f, (w - 1).toFloat())
                val x0 = sx.toInt()
                val x1 = (x0 + 1).coerceAtMost(w - 1)
                val wx = sx - x0

                val i00 = (y0 * w + x0) * 4
                val i01 = (y0 * w + x1) * 4
                val i10 = (y1 * w + x0) * 4
                val i11 = (y1 * w + x1) * 4

                val o = oy * EDGE + ox
                // BGRA in source -> R, G, B planes out. Channel offset within the pixel: R=2, G=1, B=0.
                for (c in 0 until 3) {
                    val off = 2 - c
                    val top = lerp(u8(src[i00 + off]), u8(src[i01 + off]), wx)
                    val bot = lerp(u8(src[i10 + off]), u8(src[i11 + off]), wx)
                    val value = lerp(top, bot, wy) / 255f
                    out[c * plane + o] = (value - MEAN[c]) / STD[c]
                }
            }
        }
        return out
    }

    override fun close() {
        session.close()
    }

    private companion object {
        const val EDGE = 224
        const val DEFAULT_RESOURCE = "/models/mobilenetv3-small.onnx"
        const val DEFAULT_ID = "mobilenetv3-small-100-v1"

        val INPUT_SHAPE = longArrayOf(1, 3, EDGE.toLong(), EDGE.toLong())

        // ImageNet RGB normalization the backbone was trained with.
        val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        private fun u8(b: Byte): Float = (b.toInt() and 0xFF).toFloat()
        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private fun normalizeL2(vec: FloatArray): FloatArray {
            var norm = 0f
            for (v in vec) norm += v * v
            norm = kotlin.math.sqrt(norm)
            if (norm > 1e-6f) {
                for (i in vec.indices) vec[i] /= norm
            }
            return vec
        }
    }

    /**
     * Loads the bundled ONNX model from the classpath and opens a session. Throws if the resource is
     * missing or the model fails to load; callers (see `AppContainer`) fall back to the classical
     * embedder so a packaging slip degrades gracefully rather than breaking the app.
     */
    object Loader {
        fun fromResource(
            resourcePath: String = DEFAULT_RESOURCE,
            id: String = DEFAULT_ID,
        ): OnnxEmbeddingModel {
            val bytes = OnnxEmbeddingModel::class.java.getResourceAsStream(resourcePath)
                ?.use { it.readBytes() }
                ?: error("Embedding model resource not found on classpath: $resourcePath")

            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(bytes, OrtSession.SessionOptions())
            val inputName = session.inputNames.first()
            val dimensions = probeDimensions(env, session, inputName)
            return OnnxEmbeddingModel(id, dimensions, env, session, inputName)
        }

        // The exported graph has a dynamic batch axis, so the output width isn't always static in the
        // metadata. One zero-input run reports the true embedding length unambiguously.
        private fun probeDimensions(env: OrtEnvironment, session: OrtSession, inputName: String): Int {
            val zeros = FloatArray(3 * EDGE * EDGE)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(zeros), INPUT_SHAPE).use { tensor ->
                session.run(mapOf(inputName to tensor)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    return (result[0].value as Array<FloatArray>)[0].size
                }
            }
        }
    }
}
