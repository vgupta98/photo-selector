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
