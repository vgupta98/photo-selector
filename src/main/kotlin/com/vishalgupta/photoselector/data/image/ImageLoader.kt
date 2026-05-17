package com.vishalgupta.photoselector.data.image

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.domain.model.Photo

interface ImageLoader {
    suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap?
    fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int)
    fun evictAll()
    fun pin(id: com.vishalgupta.photoselector.domain.model.PhotoId)
    fun unpinAllExcept(id: com.vishalgupta.photoselector.domain.model.PhotoId?)
}
