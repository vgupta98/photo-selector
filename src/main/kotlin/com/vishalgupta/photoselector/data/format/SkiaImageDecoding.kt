package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.model.DecodedImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
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
            return resample(source, srcW, srcH, outW, outH, what = path.toString())
        } finally {
            source.close()
        }
    }

    /**
     * Scales [image] *up* so its long edge is [targetLongEdgePx], returning it unchanged when it is
     * already at least that large (downscaling is [decode]'s job, via its cap).
     *
     * Sharpness scoring uses this to put every frame of a cluster on one canonical canvas.
     * Variance-of-Laplacian is a *per-pixel* measure, so a small frame packs each edge into steeper
     * pixel-to-pixel jumps and would out-score a larger, genuinely sharper frame — which made the
     * lowest-resolution copy in a cluster win as the suggested key frame. Upscaling the small frame
     * to the shared canvas instead exposes its lack of detail (bilinear makes it smooth, hence low
     * variance), so the comparison ranks real sharpness rather than native pixel density.
     */
    fun scaleUpToLongEdge(image: DecodedImage, targetLongEdgePx: Int): DecodedImage {
        val longest = maxOf(image.width, image.height)
        if (targetLongEdgePx <= 0 || longest >= targetLongEdgePx) return image

        val scale = targetLongEdgePx.toFloat() / longest.toFloat()
        val outW = (image.width * scale).toInt().coerceAtLeast(1)
        val outH = (image.height * scale).toInt().coerceAtLeast(1)
        val srcInfo = bgraInfo(image.width, image.height)
        val source = Image.makeRaster(srcInfo, image.bgraBytes, srcInfo.minRowBytes)
        try {
            return resample(source, image.width, image.height, outW, outH, what = "scaleUpToLongEdge")
        } finally {
            source.close()
        }
    }

    /** Draws [source] into an [outW] x [outH] BGRA raster surface and reads the pixels back out. */
    private fun resample(source: Image, srcW: Int, srcH: Int, outW: Int, outH: Int, what: String): DecodedImage {
        val info = bgraInfo(outW, outH)
        val surface = Surface.makeRaster(info)
        try {
            surface.canvas.drawImageRect(
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
                    ?: error("Could not read pixels for: $what")
                return DecodedImage(width = outW, height = outH, bgraBytes = bytes)
            } finally {
                bitmap.close()
            }
        } finally {
            surface.close()
        }
    }

    private fun bgraInfo(width: Int, height: Int) = ImageInfo(
        colorInfo = ColorInfo(
            colorType = ColorType.BGRA_8888,
            alphaType = ColorAlphaType.PREMUL,
            colorSpace = ColorSpace.sRGB,
        ),
        width = width,
        height = height,
    )

    private fun computeScale(srcW: Int, srcH: Int, targetMaxDim: Int?): Float {
        if (targetMaxDim == null || targetMaxDim <= 0) return 1f
        val longest = maxOf(srcW, srcH)
        return if (longest <= targetMaxDim) 1f
        else targetMaxDim.toFloat() / longest.toFloat()
    }
}
