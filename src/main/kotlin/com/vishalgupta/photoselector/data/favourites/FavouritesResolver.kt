package com.vishalgupta.photoselector.data.favourites

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId

/**
 * Re-attaches persisted favourite [PhotoEntryDto]s to the photos found in the current
 * scan, so a favourite survives a folder rename or a moved file. For each entry, in order:
 *
 *  1. exact root-relative path match — the common case, zero cost;
 *  2. `(size, mtime)` match among scanned photos — survives renames/moves that preserve
 *     file identity (ties broken by longest common path prefix to the old path);
 *  3. otherwise the favourite is an orphan and is dropped.
 *
 * Path-only entries (size/mtime < 0: legacy v1 files) only ever match by path — there is
 * no identity hint to fall back on. The result is keyed by the photo's *current* [PhotoId],
 * so the next write persists the up-to-date path.
 */
object FavouritesResolver {
    fun resolve(entries: List<PhotoEntryDto>, scanned: List<Photo>): Set<PhotoId> {
        if (entries.isEmpty() || scanned.isEmpty()) return emptySet()
        val byPath = scanned.associateBy { it.relativePath }
        val bySizeMtime = scanned.groupBy { it.sizeBytes to it.lastModifiedEpochMs }

        val result = LinkedHashSet<PhotoId>()
        for (entry in entries) {
            val exact = byPath[entry.path]
            if (exact != null) {
                result += exact.id
                continue
            }
            if (entry.size >= 0 && entry.mtimeMs >= 0) {
                val candidates = bySizeMtime[entry.size to entry.mtimeMs].orEmpty()
                val match = when (candidates.size) {
                    0 -> null
                    1 -> candidates.first()
                    else -> candidates.maxByOrNull { it.relativePath.commonPrefixWith(entry.path).length }
                }
                if (match != null) result += match.id
            }
            // else: orphan — drop silently.
        }
        return result
    }
}
