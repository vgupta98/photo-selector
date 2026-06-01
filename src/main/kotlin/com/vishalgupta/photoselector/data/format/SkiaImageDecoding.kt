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
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.nio.file.Files
import java.nio.file.Path

internal object SkiaImageDecoding {
    fun decode(path: Path, targetMaxDimensionPx: Int?): DecodedImage {
        val encoded = Files.readAllBytes(path)
        val source = Image.makeFromEncoded(encoded)
        try {
            val srcW = source.width
            val srcH = source.height
            if (srcW <= 0 || srcH <= 0) error("Decoded image has zero dimension: $path")

            val scale = computeScale(srcW, srcH, targetMaxDimensionPx)
            val outW = (srcW * scale).toInt().coerceAtLeast(1)
            val outH = (srcH * scale).toInt().coerceAtLeast(1)

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
                canvas.drawImageRect(
                    image = source,
                    src = Rect.makeXYWH(0f, 0f, srcW.toFloat(), srcH.toFloat()),
                    dst = Rect.makeXYWH(0f, 0f, outW.toFloat(), outH.toFloat()),
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
                    return DecodedImage(width = outW, height = outH, bgraBytes = bytes)
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

    @Suppress("unused")
    private fun fullRect(w: Int, h: Int) = IRect.makeXYWH(0, 0, w, h)
}
