package com.vishalgupta.photoselector.data.image

import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension

class DiskThumbnailCache(
    private val cacheDir: Path,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val thumbsDir = cacheDir.resolve("thumbs")

    fun startEviction() {
        Thread({ evict() }, "disk-cache-eviction").apply { isDaemon = true }.start()
    }

    fun get(photo: Photo, targetEdgePx: Int): DecodedImage? {
        val file = cacheFileFor(photo, targetEdgePx)
        if (!file.exists()) return null
        return try {
            val jpegBytes = Files.readAllBytes(file)
            decodeJpeg(jpegBytes)
        } catch (_: Throwable) {
            file.deleteIfExists()
            null
        }
    }

    fun put(photo: Photo, targetEdgePx: Int, decoded: DecodedImage) {
        val file = cacheFileFor(photo, targetEdgePx)
        try {
            val jpegBytes = encodeToJpeg(decoded)
            file.parent.createDirectories()
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            Files.write(tmp, jpegBytes)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Throwable) {
            // Non-fatal — next session will just re-decode this thumbnail
        }
    }

    private fun evict() {
        if (!thumbsDir.exists()) return
        try {
            data class CacheFile(val path: Path, val size: Long, val lastModified: Long)

            val files = thumbsDir.toFile().walkTopDown()
                .filter { it.isFile && it.extension == "jpg" }
                .map { CacheFile(it.toPath(), it.length(), it.lastModified()) }
                .toMutableList()
            val totalSize = files.sumOf { it.size }
            if (totalSize <= maxBytes) return
            files.sortBy { it.lastModified }
            var remaining = totalSize
            for (f in files) {
                if (remaining <= maxBytes) break
                f.path.deleteIfExists()
                remaining -= f.size
            }
        } catch (_: Throwable) {
            // Eviction is best-effort
        }
    }

    private fun cacheFileFor(photo: Photo, targetEdgePx: Int): Path {
        val input = "${photo.absolutePath}|${photo.sizeBytes}|${photo.lastModifiedEpochMs}|$targetEdgePx|$CACHE_VERSION"
        val hash = sha256Hex(input)
        val shard = hash.substring(0, 2)
        return thumbsDir.resolve(shard).resolve("$hash.jpg")
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun encodeToJpeg(decoded: DecodedImage): ByteArray {
        val info = bgraImageInfo(decoded.width, decoded.height)
        val bitmap = Bitmap()
        try {
            bitmap.allocPixels(info)
            bitmap.installPixels(info, decoded.bgraBytes, info.minRowBytes)
            val image = Image.makeFromBitmap(bitmap)
            try {
                return image.encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)?.bytes
                    ?: error("JPEG encoding failed")
            } finally {
                image.close()
            }
        } finally {
            bitmap.close()
        }
    }

    private fun decodeJpeg(bytes: ByteArray): DecodedImage {
        val source = Image.makeFromEncoded(bytes)
        try {
            val w = source.width
            val h = source.height
            if (w <= 0 || h <= 0) error("Cached thumbnail has zero dimension")
            val info = bgraImageInfo(w, h)
            val surface = Surface.makeRaster(info)
            try {
                surface.canvas.drawImageRect(
                    image = source,
                    src = Rect.makeXYWH(0f, 0f, w.toFloat(), h.toFloat()),
                    dst = Rect.makeXYWH(0f, 0f, w.toFloat(), h.toFloat()),
                    samplingMode = SamplingMode.LINEAR,
                    paint = null,
                    strict = true,
                )
                val bitmap = Bitmap()
                try {
                    bitmap.allocPixels(info)
                    surface.readPixels(bitmap, 0, 0)
                    val pixels = bitmap.readPixels(info, info.minRowBytes, 0, 0)
                        ?: error("Failed to read pixels from cached thumbnail")
                    return DecodedImage(width = w, height = h, bgraBytes = pixels)
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

    companion object {
        const val CACHE_VERSION = 1
        const val JPEG_QUALITY = 85
        const val DEFAULT_MAX_BYTES: Long = 1024L * 1024 * 1024
        const val MAX_EDGE_PX = 384

        private fun bgraImageInfo(width: Int, height: Int) = ImageInfo(
            colorInfo = ColorInfo(
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.PREMUL,
                colorSpace = ColorSpace.sRGB,
            ),
            width = width,
            height = height,
        )
    }
}
