package com.vishalgupta.photoselector.data.categories

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId

/**
 * Re-attaches persisted [PhotoEntryDto]s to the photos found in the current scan, so a
 * category membership survives a folder rename or a moved file. For each entry, in order:
 *
 *  1. exact root-relative path match — the common case, zero cost;
 *  2. `(size, mtime)` match among scanned photos — survives renames/moves that preserve
 *     file identity (ties broken by longest common path prefix to the old path);
 *  3. otherwise the entry is an orphan and is dropped.
 *
 * Path-only entries (size/mtime < 0: migrated v1 favourites) only ever match by path —
 * there is no identity hint to fall back on. The result is keyed by the photo's *current*
 * [PhotoId], so the next write persists the up-to-date path.
 *
 * Two distinct stored entries that share the same `(size, mtime)` and whose exact paths
 * are both gone resolve to the same single candidate and collapse into one member (the
 * result is a Set). That's acceptable: identical size and mtime almost always means the
 * files are true duplicates anyway.
 */
object MembershipResolver {
    fun resolve(entries: List<PhotoEntryDto>, scanned: List<Photo>): Set<PhotoId> {
        if (entries.isEmpty() || scanned.isEmpty()) return emptySet()
        val byPath = scanned.associateBy { it.relativePath }
        // Built lazily: in the steady state (post-rename-free file) every entry resolves
        // by exact path and this index — an O(scanned) allocation — is never needed.
        var bySizeMtime: Map<Pair<Long, Long>, List<Photo>>? = null

        val result = LinkedHashSet<PhotoId>()
        for (entry in entries) {
            val exact = byPath[entry.path]
            if (exact != null) {
                result += exact.id
                continue
            }
            if (entry.size >= 0 && entry.mtimeMs >= 0) {
                val index = bySizeMtime
                    ?: scanned.groupBy { it.sizeBytes to it.lastModifiedEpochMs }.also { bySizeMtime = it }
                val candidates = index[entry.size to entry.mtimeMs].orEmpty()
                val match = when (candidates.size) {
                    0 -> null
                    1 -> candidates.first()
                    // Tie-break is intentionally character-level (not path-segment) and
                    // best-effort: candidates here share identical size+mtime, so they're
                    // almost certainly duplicate content and the choice rarely matters.
                    else -> candidates.maxByOrNull { it.relativePath.commonPrefixWith(entry.path).length }
                }
                if (match != null) result += match.id
            }
            // else: orphan — drop silently.
        }
        return result
    }
}
