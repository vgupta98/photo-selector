package com.vishalgupta.photoselector.data.categories

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class MembershipResolverTest {

    private fun photo(relative: String, size: Long, mtime: Long) = Photo(
        id = PhotoId(relative),
        absolutePath = Path.of("/root/$relative"),
        relativePath = relative,
        fileName = relative.substringAfterLast('/'),
        sizeBytes = size,
        lastModifiedEpochMs = mtime,
    )

    @Test
    fun exactPathMatch_resolvesToThatPhoto() {
        val scanned = listOf(photo("Ceremony/a.jpg", 100, 1), photo("Ceremony/b.jpg", 200, 2))
        val entries = listOf(PhotoEntryDto("Ceremony/b.jpg", 200, 2))

        assertEquals(setOf(PhotoId("Ceremony/b.jpg")), MembershipResolver.resolve(entries, scanned))
    }

    @Test
    fun renamedFolder_reattachesViaSizeAndMtime() {
        // The membership was stored under the old folder name; the scan now sees the new name.
        val scanned = listOf(photo("01-Ceremony/a.jpg", 100, 1700))
        val entries = listOf(PhotoEntryDto("Ceremony/a.jpg", 100, 1700))

        assertEquals(setOf(PhotoId("01-Ceremony/a.jpg")), MembershipResolver.resolve(entries, scanned))
    }

    @Test
    fun sizeMtimeTie_prefersLongestCommonPathPrefix() {
        val scanned = listOf(
            photo("Reception/x.jpg", 500, 9),
            photo("Ceremony/x.jpg", 500, 9),
        )
        val entries = listOf(PhotoEntryDto("Ceremony/old.jpg", 500, 9))

        // Both candidates share (size, mtime); the Ceremony/ one shares more path prefix.
        assertEquals(setOf(PhotoId("Ceremony/x.jpg")), MembershipResolver.resolve(entries, scanned))
    }

    @Test
    fun noMatch_isDroppedAsOrphan() {
        val scanned = listOf(photo("a.jpg", 100, 1))
        val entries = listOf(PhotoEntryDto("gone.jpg", 999, 999))

        assertEquals(emptySet<PhotoId>(), MembershipResolver.resolve(entries, scanned))
    }

    @Test
    fun pathOnlyEntry_matchesByPathButNeverByFallback() {
        // A migrated legacy v1 entry (size/mtime = -1). The path no longer exists, and the
        // sentinel must not be treated as a real (size, mtime) to match against.
        val scanned = listOf(photo("renamed.jpg", -1, -1))
        val entries = listOf(PhotoEntryDto(path = "original.jpg"))

        assertEquals(emptySet<PhotoId>(), MembershipResolver.resolve(entries, scanned))
    }
}
