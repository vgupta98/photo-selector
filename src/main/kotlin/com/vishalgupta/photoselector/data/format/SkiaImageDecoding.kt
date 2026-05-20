package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.model.DecodedImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

internal object SkiaImageDecoding {
    /**
     * Decode an image and produce ARGB pixels already oriented for display.
     *
     * @param orientation EXIF orientation value (1..8). Defaults to 1 (identity).
     *     Values 5..8 swap width/height; the returned [DecodedImage] always
     *     uses post-rotation dimensions and the [targetMaxDimensionPx] cap
     *     applies to the longest edge **after** rotation.
     */
    fun decode(
        path: Path,
        targetMaxDimensionPx: Int?,
        orientation: Int = 1,
    ): DecodedImage {
        val encoded = Files.readAllBytes(path)
        val source = Image.makeFromEncoded(encoded)
        try {
            val srcW = source.width
            val srcH = source.height
            if (srcW <= 0 || srcH <= 0) error("Decoded image has zero dimension: $path")

            val swap = orientation in 5..8
            val dispW = if (swap) srcH else srcW
            val dispH = if (swap) srcW else srcH

            val scale = computeScale(dispW, dispH, targetMaxDimensionPx)
            val outW = (dispW * scale).toInt().coerceAtLeast(1)
            val outH = (dispH * scale).toInt().coerceAtLeast(1)

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
                val canvas: Canvas = surface.canvas
                canvas.concat(orientationMatrix(orientation, srcW, srcH, scale))
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
                    val bytes = bitmap.readPixels(info, info.minRowBytes, 0, 0)
                        ?: error("Could not read pixels for: $path")
                    val pixels = bgraBytesToArgbInts(bytes, outW, outH)
                    return DecodedImage(width = outW, height = outH, argbPixels = pixels)
                } finally {
                    bitmap.close()
                }
            } finally {
                surface.close()
            }
        } finally {
            source.close()
        }
    }

    private fun computeScale(srcW: Int, srcH: Int, targetMaxDim: Int?): Float {
        if (targetMaxDim == null || targetMaxDim <= 0) return 1f
        val longest = maxOf(srcW, srcH)
        return if (longest <= targetMaxDim) 1f
        else targetMaxDim.toFloat() / longest.toFloat()
    }

    /**
     * Build the affine transform that maps source pixel coordinates
     * (0..srcW, 0..srcH) onto the output surface, applying the EXIF
     * orientation and uniform scale in one shot.
     *
     * Matrix layout follows Skia's row-major Matrix33:
     *   x' = a*x + b*y + c
     *   y' = d*x + e*y + f
     */
    private fun orientationMatrix(
        orientation: Int,
        srcW: Int,
        srcH: Int,
        scale: Float,
    ): Matrix33 {
        val w = srcW.toFloat()
        val h = srcH.toFloat()
        val s = scale
        val (a, b, c, d, e, f) = when (orientation) {
            2 -> Tuple6(-s, 0f, s * w, 0f, s, 0f)                  // mirror horizontal
            3 -> Tuple6(-s, 0f, s * w, 0f, -s, s * h)              // rotate 180
            4 -> Tuple6(s, 0f, 0f, 0f, -s, s * h)                  // mirror vertical
            5 -> Tuple6(0f, s, 0f, s, 0f, 0f)                      // transpose
            6 -> Tuple6(0f, -s, s * h, s, 0f, 0f)                  // rotate 90 CW
            7 -> Tuple6(0f, -s, s * h, -s, 0f, s * w)              // transverse
            8 -> Tuple6(0f, s, 0f, -s, 0f, s * w)                  // rotate 90 CCW
            else -> Tuple6(s, 0f, 0f, 0f, s, 0f)                   // 1 (and unknown)
        }
        return Matrix33(
            a, b, c,
            d, e, f,
            0f, 0f, 1f,
        )
    }

    private data class Tuple6(
        val a: Float, val b: Float, val c: Float,
        val d: Float, val e: Float, val f: Float,
    )

    /** Skia row order is BGRA bytes, little-endian, premul alpha. Convert to ARGB ints. */
    private fun bgraBytesToArgbInts(bytes: ByteArray, width: Int, height: Int): IntArray {
        val expected = width * height * 4
        require(bytes.size >= expected) {
            "Pixel buffer too small: expected $expected got ${bytes.size}"
        }
        val out = IntArray(width * height)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until width * height) {
            val b = buf.get().toInt() and 0xFF
            val g = buf.get().toInt() and 0xFF
            val r = buf.get().toInt() and 0xFF
            val a = buf.get().toInt() and 0xFF
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    @Suppress("unused")
    private fun fullRect(w: Int, h: Int) = IRect.makeXYWH(0, 0, w, h)
}
