package com.vishalgupta.photoselector.data.image

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.domain.model.PhotoId

internal data class CacheKey(val id: PhotoId, val viewportLongEdgePx: Int)

internal class ImageCache(private val maxBytes: Long) {

    private data class Entry(val bitmap: ImageBitmap, val byteSize: Long)

    private val lock = Any()
    private val entries = LinkedHashMap<CacheKey, Entry>(64, 0.75f, true)
    private val pinned = HashSet<PhotoId>()
    private var currentBytes: Long = 0

    fun get(key: CacheKey): ImageBitmap? = synchronized(lock) {
        entries[key]?.bitmap
    }

    fun put(key: CacheKey, bitmap: ImageBitmap, byteSize: Long) = synchronized(lock) {
        val existing = entries.put(key, Entry(bitmap, byteSize))
        if (existing != null) currentBytes -= existing.byteSize
        currentBytes += byteSize
        evictIfNeeded()
    }

    fun pin(id: PhotoId) = synchronized(lock) {
        pinned += id
    }

    fun unpinAllExcept(id: PhotoId?) = synchronized(lock) {
        pinned.clear()
        if (id != null) pinned += id
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        pinned.clear()
        currentBytes = 0
    }

    private fun evictIfNeeded() {
        if (currentBytes <= maxBytes) return
        val it = entries.entries.iterator()
        while (it.hasNext() && currentBytes > maxBytes) {
            val (key, entry) = it.next()
            if (key.id in pinned) continue
            currentBytes -= entry.byteSize
            it.remove()
        }
    }
}
