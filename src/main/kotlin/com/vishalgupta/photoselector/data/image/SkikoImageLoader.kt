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
import kotlinx.coroutines.NonCancellable
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

class SkikoImageLoader(
    private val registry: PhotoFormatRegistry,
    private val decodeDispatcher: CoroutineDispatcher,
    private val diskCache: DiskThumbnailCache? = null,
    maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
) : ImageLoader {

    private val cache = ImageCache(maxCacheBytes)
    private val inflight = HashMap<CacheKey, Job>()
    private val inflightLock = Mutex()

    override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? {
        val key = CacheKey(photo.id, viewportLongEdgePx)
        cache.get(key)?.let { return it }

        val useDisk = diskCache != null && viewportLongEdgePx <= DiskThumbnailCache.MAX_EDGE_PX
        if (useDisk) {
            val diskHit = withContext(decodeDispatcher) {
                diskCache!!.get(photo, viewportLongEdgePx)
            }
            if (diskHit != null) {
                val bitmap = diskHit.toImageBitmap()
                cache.put(key, bitmap, diskHit.byteSize)
                return bitmap
            }
        }

        val decoder = registry.decoderFor(photo.absolutePath) ?: return null
        return try {
            val decoded = withContext(decodeDispatcher) {
                decoder.decode(photo.absolutePath, viewportLongEdgePx)
            }
            val bitmap = decoded.toImageBitmap()
            cache.put(key, bitmap, decoded.byteSize)
            if (useDisk) {
                withContext(decodeDispatcher) {
                    diskCache!!.put(photo, viewportLongEdgePx, decoded)
                }
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    override fun prefetch(
        photos: List<Photo>,
        viewportLongEdgePx: Int,
        scope: CoroutineScope,
    ) {
        for (photo in photos) {
            val key = CacheKey(photo.id, viewportLongEdgePx)
            if (cache.get(key) != null) continue
            scope.launch(decodeDispatcher) {
                inflightLock.withLock {
                    if (inflight[key]?.isActive == true) return@launch
                    inflight[key] = coroutineContext[Job]!!
                }
                try {
                    load(photo, viewportLongEdgePx)
                } finally {
                    // Cleanup must complete even when the caller's scope is being
                    // torn down — otherwise the inflight map leaks the key.
                    withContext(NonCancellable) {
                        inflightLock.withLock { inflight.remove(key) }
                    }
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
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        bitmap.installPixels(info, bgraBytes, info.minRowBytes)
        return bitmap.asComposeImageBitmap()
    }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES: Long = 512L * 1024 * 1024
    }
}
