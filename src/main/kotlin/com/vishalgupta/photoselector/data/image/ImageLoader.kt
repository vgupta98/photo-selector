package com.vishalgupta.photoselector.data.image

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.CoroutineScope

interface ImageLoader {
    suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap?

    /**
     * Decode [photos] into the cache on [scope]. Cancelling [scope] cancels every
     * in-flight decode launched here, so the caller owns lifetime — no orphan work
     * lingers after the caller goes away.
     */
    fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope)

    fun evictAll()
    fun pin(id: PhotoId)
    fun unpinAllExcept(id: PhotoId?)
}
