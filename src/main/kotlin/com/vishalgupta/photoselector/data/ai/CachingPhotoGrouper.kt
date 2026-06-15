package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.grouping.GroupingProgress
import com.vishalgupta.photoselector.domain.grouping.PhotoGrouper
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A [PhotoGrouper] decorator that memoizes the *computed grouping* to a [GroupingResultCache], so
 * re-entering a lens on an unchanged folder returns instantly instead of re-running [delegate]'s
 * pass. Wraps the expensive Similarity grouper; the cheap Time grouper has no need for it.
 *
 * On a hit it reports progress complete and returns at once — the grid's grace-delayed progress bar
 * never arms, which is the "instant re-entry" the user sees. On a miss it runs [delegate] and writes
 * the result, but only if the pass completed: a cancelled pass (the grid cancels and re-runs grouping
 * on every re-slice / toggle) throws out of [delegate] before the write, so a partial grouping is
 * never cached.
 *
 * The cache is content-keyed (each frame's `path|size|mtime`, plus [modelId]), so a source edit or a
 * model swap re-keys automatically and a stale grouping can never surface.
 */
class CachingPhotoGrouper(
    private val delegate: PhotoGrouper,
    private val cache: GroupingResultCache,
    private val modelId: String,
) : PhotoGrouper {

    override suspend fun group(photos: List<Photo>, onProgress: GroupingProgress): List<PhotoGroup> {
        if (photos.isEmpty()) return emptyList()
        val key = cache.keyFor(modelId, photos)
        cache.get(key, photos)?.let { cached ->
            // Report complete so any armed determinate bar clears; the work was already done.
            onProgress(photos.size, photos.size)
            return cached
        }
        val groups = delegate.group(photos, onProgress)
        // Don't persist a grouping computed by a pass that was cancelled mid-flight.
        currentCoroutineContext().ensureActive()
        cache.put(key, groups)
        return groups
    }
}
