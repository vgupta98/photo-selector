package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.format.PhotoFormat
import com.vishalgupta.photoselector.domain.format.PhotoDecoder
import com.vishalgupta.photoselector.domain.model.DecodedImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.nio.file.Path

class JpegDecoder : PhotoDecoder {
    override val format: PhotoFormat = JpegFormat

    override suspend fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage {
        if (targetMaxDimensionPx != null && targetMaxDimensionPx <= EMBEDDED_THUMB_THRESHOLD_PX) {
            tryDecodeEmbeddedThumbnail(path, targetMaxDimensionPx)?.let { return it }
        }
        return SkiaImageDecoding.decode(path, targetMaxDimensionPx)
    }

    private fun tryDecodeEmbeddedThumbnail(path: Path, target: Int): DecodedImage? {
        val thumb = ExifReader.readEmbeddedThumbnail(path) ?: return null
        val source = try {
            Image.makeFromEncoded(thumb.bytes)
        } catch (_: Throwable) {
            return null
        }
        try {
            val srcW = source.width
            val srcH = source.height
            if (srcW <= 0 || srcH <= 0) return null

            val swap = thumb.orientation in 5..8
            val postW = if (swap) srcH else srcW
            val postH = if (swap) srcW else srcH

            val longest = maxOf(postW, postH)
            val scale = if (longest <= target) 1f else target.toFloat() / longest.toFloat()
            val outW = (postW * scale).toInt().coerceAtLeast(1)
            val outH = (postH * scale).toInt().coerceAtLeast(1)

            // Embedded thumb quality is borderline at small thresholds; reject the fast path
            // if the thumb is smaller than what the caller asked for, so we fall back to the
            // full decode rather than upscaling an already-soft 160px image.
            if (longest < target / 2) return null

            val info = ImageInfo(
                colorInfo = ColorInfo(
                    colorType = ColorType.BGRA_8888,
                    alphaType = ColorAlphaType.PREMUL,
                    colorSpace = ColorSpace.sRGB,
                ),
                width = outW,
                height = outH,
            )

            val surface = Surface.makeRaster(info)
            try {
                val canvas = surface.canvas
                canvas.concat(orientationMatrix(thumb.orientation, scale, outW.toFloat(), outH.toFloat()))
                canvas.drawImageRect(
                    image = source,
                    src = Rect.makeXYWH(0f, 0f, srcW.toFloat(), srcH.toFloat()),
                    dst = Rect.makeXYWH(0f, 0f, srcW.toFloat(), srcH.toFloat()),
                    samplingMode = SamplingMode.LINEAR,
                    paint = null,
                    strict = true,
                )

                val bitmap = Bitmap()
                try {
                    bitmap.allocPixels(info)
                    surface.readPixels(bitmap, 0, 0)
                    val bytes = bitmap.readPixels(info, info.minRowBytes, 0, 0) ?: return null
                    return DecodedImage(width = outW, height = outH, bgraBytes = bytes)
                } finally {
                    bitmap.close()
                }
            } finally {
                surface.close()
            }
        } catch (_: Throwable) {
            return null
        } finally {
            source.close()
        }
    }

    companion object {
        // Comfortably above the favourites grid's 320 px request, well below the 1600+ px the
        // full browser view asks for. Keeps the browser on the full-decode path.
        internal const val EMBEDDED_THUMB_THRESHOLD_PX = 384

        object JpegFormat : PhotoFormat {
            override val id: String = "jpeg"
            override val extensions: Set<String> = setOf("jpg", "jpeg")
        }

        /**
         * Affine transform that maps source pixel (sx, sy) into the oriented, scaled output canvas.
         * `outW`/`outH` are post-orientation, post-scale.
         */
        internal fun orientationMatrix(orientation: Int, s: Float, outW: Float, outH: Float): Matrix33 =
            when (orientation) {
                2 -> Matrix33(-s, 0f, outW,  0f, s, 0f,    0f, 0f, 1f)
                3 -> Matrix33(-s, 0f, outW,  0f, -s, outH, 0f, 0f, 1f)
                4 -> Matrix33(s, 0f, 0f,     0f, -s, outH, 0f, 0f, 1f)
                5 -> Matrix33(0f, s, 0f,     s, 0f, 0f,    0f, 0f, 1f)
                6 -> Matrix33(0f, -s, outW,  s, 0f, 0f,    0f, 0f, 1f)
                7 -> Matrix33(0f, -s, outW,  -s, 0f, outH, 0f, 0f, 1f)
                8 -> Matrix33(0f, s, 0f,     -s, 0f, outH, 0f, 0f, 1f)
                else -> Matrix33(s, 0f, 0f,  0f, s, 0f,    0f, 0f, 1f)
            }
    }
}
