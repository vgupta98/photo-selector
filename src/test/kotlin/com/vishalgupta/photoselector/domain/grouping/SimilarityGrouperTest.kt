package com.vishalgupta.photoselector.domain.grouping

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimilarityGrouperTest {

    @Test fun `empty input yields no groups`() {
        assertTrue(SimilarityGrouper.group(emptyList(), emptyMap()).isEmpty())
    }

    @Test fun `a lone photo is a single`() {
        val p = photo("IMG_1")
        val groups = SimilarityGrouper.group(listOf(p), mapOf(p.id to unit(1f, 0f)))
        assertIs<PhotoGroup.Single>(groups.single())
    }

    @Test fun `adjacent near-identical frames group regardless of capture time`() {
        // The visual lens has no time concept: two near-identical shots taken far apart still merge.
        val a = photo("A"); val b = photo("B")
        val groups = SimilarityGrouper.group(
            listOf(a, b),
            mapOf(a.id to unit(1f, 0f), b.id to unit(1f, 0.2f)), // cosine ~0.98
        )
        assertEquals(listOf(a, b), assertIs<PhotoGroup.Burst>(groups.single()).photos)
    }

    @Test fun `visually different adjacent frames stay separate`() {
        val a = photo("A"); val b = photo("B")
        val groups = SimilarityGrouper.group(
            listOf(a, b),
            mapOf(a.id to unit(1f, 0f), b.id to unit(0f, 1f)), // orthogonal, cosine 0
        )
        assertEquals(2, groups.size)
        assertTrue(groups.all { it is PhotoGroup.Single })
    }

    @Test fun `a folder boundary splits even visually identical frames`() {
        val a = photo("A", folder = "/root/Ceremony")
        val b = photo("B", folder = "/root/Reception")
        val groups = SimilarityGrouper.group(
            listOf(a, b),
            mapOf(a.id to unit(1f, 0f), b.id to unit(1f, 0f)),
        )
        assertEquals(2, groups.size)
    }

    @Test fun `a frame with no embedding never joins a cluster`() {
        val a = photo("A"); val b = photo("B"); val c = photo("C")
        val groups = SimilarityGrouper.group(
            listOf(a, b, c),
            // b has no embedding, so it can't bridge a->c even though a and c match.
            mapOf(a.id to unit(1f, 0f), c.id to unit(1f, 0f)),
        )
        assertEquals(3, groups.size)
        assertTrue(groups.all { it is PhotoGroup.Single })
    }

    @Test fun `the sharpest frame is the cluster representative`() {
        val photos = (1..3).map { photo("IMG_$it") }
        val emb = photos.associate { it.id to unit(1f, 0f) } // all identical -> one burst
        val sharpness = mapOf(
            photos[0].id to 5f,
            photos[1].id to 9f, // middle happens to be sharpest here
            photos[2].id to 1f,
        )
        val burst = assertIs<PhotoGroup.Burst>(
            SimilarityGrouper.group(photos, emb, sharpness).single(),
        )
        assertEquals(photos[1], burst.keyPhoto)

        // Flip it: make the first frame sharpest and the key must follow, not stay in the middle.
        val firstSharpest = mapOf(photos[0].id to 9f, photos[1].id to 2f, photos[2].id to 1f)
        val burst2 = assertIs<PhotoGroup.Burst>(
            SimilarityGrouper.group(photos, emb, firstSharpest).single(),
        )
        assertEquals(photos[0], burst2.keyPhoto)
    }

    @Test fun `with no sharpness known the middle frame represents the cluster`() {
        val photos = (1..5).map { photo("IMG_$it") }
        val emb = photos.associate { it.id to unit(1f, 0f) }
        val burst = assertIs<PhotoGroup.Burst>(SimilarityGrouper.group(photos, emb).single())
        assertEquals(photos[2], burst.keyPhoto)
    }

    @Test fun `a mixed run produces burst single burst in order`() {
        val b1 = photo("B1"); val b2 = photo("B2")
        val lone = photo("Lone")
        val c1 = photo("C1"); val c2 = photo("C2")
        val photos = listOf(b1, b2, lone, c1, c2)
        val emb = mapOf(
            b1.id to unit(1f, 0f), b2.id to unit(1f, 0f),
            lone.id to unit(0f, 1f),
            c1.id to unit(1f, 1f), c2.id to unit(1f, 1f),
        )
        val groups = SimilarityGrouper.group(photos, emb)
        assertEquals(3, groups.size)
        assertEquals(listOf(b1, b2), assertIs<PhotoGroup.Burst>(groups[0]).photos)
        assertEquals(lone, assertIs<PhotoGroup.Single>(groups[1]).photo)
        assertEquals(listOf(c1, c2), assertIs<PhotoGroup.Burst>(groups[2]).photos)
    }

    // --- adaptive (default) threshold rule ---

    @Test fun `adaptive cut is stricter in a tight event than a fixed floor`() {
        // A "tight" event: every neighbour is fairly similar (cosine ~0.90), with one standout pair
        // (~0.99) that is genuinely the same moment. Frames placed at exact angles so the cosines are
        // known: C0-C1 ~0.90, C1-C2 ~0.99, C2-C3 ~0.90.
        val c = listOf(photo("C0"), photo("C1"), photo("C2"), photo("C3"))
        val emb = mapOf(
            c[0].id to unit(1f, 0f),
            c[1].id to unit(0.9f, 0.4359f), // 25.84 deg from C0 -> cos ~0.90
            c[2].id to unit(0.8294f, 0.5586f), // +8.11 deg -> C1-C2 cos ~0.99
            c[3].id to unit(0.5034f, 0.8640f), // +25.84 deg -> C2-C3 cos ~0.90
        )

        // Adaptive: run median ~0.90, cut ~0.97 -> only the standout pair groups; the rest stay single.
        val adaptive = SimilarityGrouper.group(c, emb)
        assertEquals(3, adaptive.size)
        assertIs<PhotoGroup.Single>(adaptive[0])
        assertEquals(listOf(c[1], c[2]), assertIs<PhotoGroup.Burst>(adaptive[1]).photos)
        assertIs<PhotoGroup.Single>(adaptive[2])

        // The old fixed 0.85 floor would over-merge the whole tight run into one burst.
        val fixed = SimilarityGrouper.group(c, emb, rule = SimilarityGrouper.fixed(0.85f))
        assertEquals(c, assertIs<PhotoGroup.Burst>(fixed.single()).photos)
    }

    @Test fun `adaptive never groups a run with no comparable pairs`() {
        // No embeddings at all -> no cosines -> cut pinned at the max, nothing groups.
        val photos = (1..3).map { photo("IMG_$it") }
        val groups = SimilarityGrouper.group(photos, emptyMap())
        assertEquals(3, groups.size)
        assertTrue(groups.all { it is PhotoGroup.Single })
    }

    @Test fun `adaptive clamp floor keeps near-orthogonal frames apart`() {
        // A folder of mutually dissimilar frames: the median is low, but the clamp floor (0.78)
        // stops the bar dropping so far that unrelated frames merge.
        val photos = listOf(photo("A"), photo("B"), photo("C"))
        val emb = mapOf(
            photos[0].id to unit(1f, 0f),
            photos[1].id to unit(0f, 1f), // orthogonal to A (cosine 0)
            photos[2].id to unit(1f, 0f), // orthogonal to B
        )
        val groups = SimilarityGrouper.group(photos, emb)
        assertEquals(3, groups.size)
        assertTrue(groups.all { it is PhotoGroup.Single })
    }

    @Test fun `the fixed rule reproduces the old constant-floor behaviour`() {
        val a = photo("A"); val b = photo("B")
        val emb = mapOf(a.id to unit(1f, 0f), b.id to unit(1f, 0.6f)) // cosine ~0.86
        // Above 0.85 -> groups; raise the floor above the pair -> splits.
        assertIs<PhotoGroup.Burst>(
            SimilarityGrouper.group(listOf(a, b), emb, rule = SimilarityGrouper.fixed(0.85f)).single(),
        )
        assertEquals(
            2,
            SimilarityGrouper.group(listOf(a, b), emb, rule = SimilarityGrouper.fixed(0.95f)).size,
        )
    }

    // --- helpers ---

    private fun unit(vararg xs: Float): FloatArray {
        var norm = 0f
        for (x in xs) norm += x * x
        norm = sqrt(norm)
        return FloatArray(xs.size) { xs[it] / norm }
    }

    private fun photo(id: String, folder: String = "/root/A"): Photo {
        val path = Path.of("$folder/$id.jpg")
        return Photo(
            id = PhotoId(id),
            absolutePath = path,
            relativePath = path.toString(),
            fileName = "$id.jpg",
            sizeBytes = 0,
            lastModifiedEpochMs = 0,
        )
    }
}
