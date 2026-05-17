package com.vishalgupta.photoselector.data.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkikoImageLoader(
    private val registry: PhotoFormatRegistry,
    private val decodeDispatcher: CoroutineDispatcher,
    maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
) : ImageLoader {

    private val cache = ImageCache(maxCacheBytes)
    private val scope = CoroutineScope(SupervisorJob() + decodeDispatcher)
    private val inflight = HashMap<CacheKey, Job>()
    private val inflightLock = Mutex()

    override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? {
        val key = CacheKey(photo.id, viewportLongEdgePx)
        cache.get(key)?.let { return it }
        val decoder = registry.decoderFor(photo.absolutePath) ?: return null
        return try {
            val decoded = withContext(decodeDispatcher) {
                decoder.decode(photo.absolutePath, viewportLongEdgePx)
            }
            val bitmap = decoded.toImageBitmap()
            cache.put(key, bitmap, decoded.byteSize)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int) {
        for (photo in photos) {
            val key = CacheKey(photo.id, viewportLongEdgePx)
            if (cache.get(key) != null) continue
            scope.launch {
                inflightLock.withLock {
                    if (inflight[key]?.isActive == true) return@launch
                    inflight[key] = coroutineContext[Job]!!
                }
                try {
                    load(photo, viewportLongEdgePx)
                } finally {
                    inflightLock.withLock { inflight.remove(key) }
                }
            }
        }
    }

    override fun evictAll() {
        cache.clear()
    }

    override fun pin(id: PhotoId) {
        cache.pin(id)
    }

    override fun unpinAllExcept(id: PhotoId?) {
        cache.unpinAllExcept(id)
    }

    private fun DecodedImage.toImageBitmap(): ImageBitmap {
        val info = ImageInfo(
            colorInfo = ColorInfo(
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.PREMUL,
                colorSpace = ColorSpace.sRGB,
            ),
            width = width,
            height = height,
        )
        val bytes = argbIntsToBgraBytes(argbPixels)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        bitmap.installPixels(info, bytes, info.minRowBytes)
        return bitmap.asComposeImageBitmap()
    }

    private fun argbIntsToBgraBytes(pixels: IntArray): ByteArray {
        val out = ByteArray(pixels.size * 4)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            buf.put(b.toByte())
            buf.put(g.toByte())
            buf.put(r.toByte())
            buf.put(a.toByte())
        }
        return out
    }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES: Long = 512L * 1024 * 1024
    }
}
