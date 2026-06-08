package com.vishalgupta.photoselector.presentation.grid

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The grid renders [buildRenderItems] while the view model's focus/selection act on
 * [displayGroupsFor]. They MUST describe the same tile sequence or a click lands on the wrong photo,
 * so the central invariant here is that the render tiles equal the display groups, index for index.
 */
class GridDisplayModelTest {

    // a | [b c d] burst | e   -> three collapsed tiles.
    private val a = photo("a")
    private val b = photo("b"); private val c = photo("c"); private val d = photo("d")
    private val e = photo("e")
    private val burst = PhotoGroup.Burst(listOf(b, c, d))
    private val groups = listOf(PhotoGroup.Single(a), burst, PhotoGroup.Single(e))

    @Test fun `collapsed display groups are the groups untouched`() {
        assertEquals(groups, displayGroupsFor(groups, expandedBurstId = null))
    }

    @Test fun `expanding a burst explodes just that burst into per-frame singles`() {
        val display = displayGroupsFor(groups, expandedBurstId = burst.groupId)
        // a, b, c, d, e -> five single tiles, in order.
        assertEquals(listOf(a, b, c, d, e), display.map { it.keyPhoto })
        assertEquals(5, display.size)
        display.forEach { assertIs<PhotoGroup.Single>(it) }
    }

    @Test fun `render tiles equal the display groups index-for-index, collapsed and expanded`() {
        for (expanded in listOf(null, burst.groupId)) {
            val renderTiles = buildRenderItems(groups, expanded)
                .filterIsInstance<GridRenderItem.Tile>()
            val display = displayGroupsFor(groups, expanded)
            // Same groups, same order, and displayIndex is the position in the display space.
            assertEquals(display, renderTiles.map { it.group })
            renderTiles.forEachIndexed { i, tile -> assertEquals(i, tile.displayIndex) }
        }
    }

    @Test fun `an expanded burst is bracketed by one header and one footer, only its frames flagged`() {
        val items = buildRenderItems(groups, expandedBurstId = burst.groupId)
        val headers = items.filterIsInstance<GridRenderItem.BurstHeader>()
        val footers = items.filterIsInstance<GridRenderItem.BurstFooter>()
        assertEquals(1, headers.size)
        assertEquals(1, footers.size)
        assertEquals(burst, headers.single().burst)
        assertEquals(burst, footers.single().burst)

        // Header immediately before the three frames, footer immediately after - they bracket the run.
        val headerPos = items.indexOfFirst { it is GridRenderItem.BurstHeader }
        val footerPos = items.indexOfFirst { it is GridRenderItem.BurstFooter }
        val flagged = items.filterIsInstance<GridRenderItem.Tile>().filter { it.expandedFrame }
        assertEquals(listOf(b, c, d), flagged.map { it.group.keyPhoto })
        assertIs<GridRenderItem.Tile>(items[headerPos + 1])
        assertEquals(headerPos + 1 + flagged.size, footerPos) // header, 3 frames, then footer
    }

    @Test fun `collapsed has no header, footer, or flagged frames`() {
        val items = buildRenderItems(groups, expandedBurstId = null)
        assertNull(items.firstOrNull { it is GridRenderItem.BurstHeader })
        assertNull(items.firstOrNull { it is GridRenderItem.BurstFooter })
        assertEquals(emptyList(), items.filterIsInstance<GridRenderItem.Tile>().filter { it.expandedFrame })
    }

    private fun photo(id: String): Photo = Photo(
        id = PhotoId(id),
        absolutePath = Path.of("/photos/$id.jpg"),
        relativePath = "$id.jpg",
        fileName = "$id.jpg",
        sizeBytes = 1,
        lastModifiedEpochMs = 0,
    )
}
