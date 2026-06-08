package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BurstGrouperTest {

    @Test fun `empty input yields no groups`() {
        assertTrue(BurstGrouper.group(emptyList(), source(emptyMap())).isEmpty())
    }

    @Test fun `a lone photo is a single`() {
        val p = photo("IMG_1")
        val groups = BurstGrouper.group(listOf(p), source(mapOf(p.id to meta(takenAtMs = 0))))
        assertEquals(1, groups.size)
        val single = assertIs<PhotoGroup.Single>(groups[0])
        assertEquals(p, single.photo)
        assertEquals(p, single.keyPhoto)
    }

    @Test fun `five sequential frames within the window form one burst with the middle as key`() {
        val photos = (1..5).map { photo("IMG_$it") }
        val metas = photos.mapIndexed { i, p -> p.id to meta(takenAtMs = i * 500L) }.toMap()

        val groups = BurstGrouper.group(photos, source(metas))

        assertEquals(1, groups.size)
        val burst = assertIs<PhotoGroup.Burst>(groups[0])
        assertEquals(photos, burst.photos)
        assertEquals(photos[2], burst.keyPhoto) // middle of 5
        assertEquals(photos[0].id, burst.groupId) // anchored to the first frame
    }

    @Test fun `exactly the max gap groups, one past it splits`() {
        val a = photo("A"); val b = photo("B")
        val atBoundary = BurstGrouper.group(
            listOf(a, b),
            source(mapOf(a.id to meta(takenAtMs = 0), b.id to meta(takenAtMs = 2_000))),
        )
        assertIs<PhotoGroup.Burst>(atBoundary.single())

        val justOver = BurstGrouper.group(
            listOf(a, b),
            source(mapOf(a.id to meta(takenAtMs = 0), b.id to meta(takenAtMs = 2_001))),
        )
        assertEquals(2, justOver.size)
        assertTrue(justOver.all { it is PhotoGroup.Single })
    }

    @Test fun `different camera bodies never merge`() {
        val a = photo("A"); val b = photo("B")
        val groups = BurstGrouper.group(
            listOf(a, b),
            source(
                mapOf(
                    a.id to meta(takenAtMs = 0, camera = "Canon EOS R5"),
                    b.id to meta(takenAtMs = 100, camera = "NIKON Z 6"),
                ),
            ),
        )
        assertEquals(2, groups.size)
    }

    @Test fun `differing orientation does not merge a flip`() {
        val a = photo("A"); val b = photo("B")
        val groups = BurstGrouper.group(
            listOf(a, b),
            source(
                mapOf(
                    a.id to meta(takenAtMs = 0, orientation = 1),
                    b.id to meta(takenAtMs = 100, orientation = 6),
                ),
            ),
        )
        assertEquals(2, groups.size)
    }

    @Test fun `frames in different folders never merge`() {
        val a = photo("A", folder = "/root/Ceremony")
        val b = photo("B", folder = "/root/Reception")
        val groups = BurstGrouper.group(
            listOf(a, b),
            source(mapOf(a.id to meta(takenAtMs = 0), b.id to meta(takenAtMs = 100))),
        )
        assertEquals(2, groups.size)
    }

    @Test fun `frames with no exif never group, even with adjacent mtimes`() {
        // mtime is not trusted: a bulk copy flattens it, so EXIF-less frames stay
        // single rather than over-grouping into a bogus burst.
        val a = photo("A", mtimeMs = 1_000)
        val b = photo("B", mtimeMs = 1_800)
        val c = photo("C", mtimeMs = 9_000)

        val groups = BurstGrouper.group(
            listOf(a, b, c),
            source(mapOf(a.id to CaptureMetadata.NONE, b.id to CaptureMetadata.NONE, c.id to CaptureMetadata.NONE)),
        )

        assertEquals(3, groups.size)
        assertTrue(groups.all { it is PhotoGroup.Single })
    }

    @Test fun `a missing capture time on either frame keeps them separate`() {
        val a = photo("A", mtimeMs = 0)
        val b = photo("B", mtimeMs = 500)
        // Same camera+orientation, but no DateTimeOriginal -> never the same burst.
        val withTime = meta(takenAtMs = 0)
        val noTime = meta(takenAtMs = null)
        val groups = BurstGrouper.group(listOf(a, b), source(mapOf(a.id to withTime, b.id to noTime)))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it is PhotoGroup.Single })
    }

    @Test fun `a mixed run produces burst single burst in order`() {
        val b1 = photo("B1"); val b2 = photo("B2"); val b3 = photo("B3")
        val lone = photo("Lone")
        val c1 = photo("C1"); val c2 = photo("C2")
        val photos = listOf(b1, b2, b3, lone, c1, c2)
        val metas = mapOf(
            b1.id to meta(takenAtMs = 0),
            b2.id to meta(takenAtMs = 100),
            b3.id to meta(takenAtMs = 200),
            lone.id to meta(takenAtMs = 10_000),
            c1.id to meta(takenAtMs = 20_000),
            c2.id to meta(takenAtMs = 20_100),
        )

        val groups = BurstGrouper.group(photos, source(metas))

        assertEquals(3, groups.size)
        assertEquals(listOf(b1, b2, b3), assertIs<PhotoGroup.Burst>(groups[0]).photos)
        assertEquals(lone, assertIs<PhotoGroup.Single>(groups[1]).photo)
        assertEquals(listOf(c1, c2), assertIs<PhotoGroup.Burst>(groups[2]).photos)
    }

    // --- helpers ---

    private fun source(map: Map<PhotoId, CaptureMetadata>) =
        CaptureMetadataSource { map[it.id] ?: CaptureMetadata.NONE }

    private fun meta(takenAtMs: Long?, camera: String? = "Canon EOS R5", orientation: Int? = 1) =
        CaptureMetadata(takenAtEpochMs = takenAtMs, cameraId = camera, orientation = orientation)

    private fun photo(id: String, folder: String = "/root/A", mtimeMs: Long = 0L): Photo {
        val path = Path.of("$folder/$id.jpg")
        return Photo(
            id = PhotoId(id),
            absolutePath = path,
            relativePath = path.toString(),
            fileName = "$id.jpg",
            sizeBytes = 0,
            lastModifiedEpochMs = mtimeMs,
        )
    }
}
