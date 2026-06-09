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

    // --- render-item index -> flat photo index (persistence / return anchors) ----------------
    //
    // The LazyGrid is keyed on render items (header/footer included when a burst is open), but the
    // flat-index map is keyed on tiles. With [burst] expanded the render-item list is
    //   0:Tile(a) 1:Header 2:Tile(b) 3:Tile(c) 4:Tile(d) 5:Footer 6:Tile(e)
    // while the tile space (and tileFlatStart) is a b c d e -> [0,1,2,3,4]. The two diverge by the
    // header/footer once a burst is open, which is the scroll/anchor-drift bug this maps away.
    private val expandedRenderItems = buildRenderItems(groups, expandedBurstId = burst.groupId)
    private val expandedTileFlatStart = listOf(0, 1, 2, 3, 4) // every frame its own tile when open

    @Test fun `a header or footer maps to the first visible tile, not an offset flat index`() {
        // Header (render 1) -> first frame b (flat 1); footer (render 5) -> post-burst tile e (flat 4).
        assertEquals(1, flatIndexForRenderItem(expandedRenderItems, expandedTileFlatStart, 1))
        assertEquals(4, flatIndexForRenderItem(expandedRenderItems, expandedTileFlatStart, 5))
    }

    @Test fun `tiles after the header map through the tile space, not the render index`() {
        // render 2 is tile b (flat 1) - the naive tileFlatStart[2] would have returned 2 (off by one).
        assertEquals(1, flatIndexForRenderItem(expandedRenderItems, expandedTileFlatStart, 2))
        // render 6 is the trailing tile e (flat 4) - naive tileFlatStart[6] is out of range -> 0, the bug.
        assertEquals(4, flatIndexForRenderItem(expandedRenderItems, expandedTileFlatStart, 6))
    }

    @Test fun `renderIndexForTile is the inverse of the render-to-tile map, and null off the end`() {
        // Expanded render items: 0:Tile(a) 1:Header 2:Tile(b) 3:Tile(c) 4:Tile(d) 5:Footer 6:Tile(e).
        // A tile displayIndex resolves to the render slot the focus effect must scroll to.
        assertEquals(0, renderIndexForTile(expandedRenderItems, 0)) // a
        assertEquals(2, renderIndexForTile(expandedRenderItems, 1)) // b - skips the header
        assertEquals(6, renderIndexForTile(expandedRenderItems, 4)) // e - skips header + footer
        assertNull(renderIndexForTile(expandedRenderItems, 99)) // no such tile

        // Round-trips with tileDisplayIndexForRenderItem for every tile.
        for (displayIndex in 0..4) {
            val renderIndex = renderIndexForTile(expandedRenderItems, displayIndex)!!
            assertEquals(displayIndex, tileDisplayIndexForRenderItem(expandedRenderItems, renderIndex))
        }
    }

    @Test fun `collapsed render items map one-to-one and empty input is safe`() {
        val collapsed = buildRenderItems(groups, expandedBurstId = null)
        assertEquals(0, flatIndexForRenderItem(collapsed, listOf(0, 1, 4), 0)) // a
        assertEquals(1, flatIndexForRenderItem(collapsed, listOf(0, 1, 4), 1)) // burst tile
        assertEquals(4, flatIndexForRenderItem(collapsed, listOf(0, 1, 4), 2)) // e
        assertEquals(0, flatIndexForRenderItem(emptyList(), emptyList(), 3)) // no items -> 0
    }

    // --- vertical keyboard navigation (geometry, not index arithmetic) -----------------------
    //
    // A 4-column grid with the burst [b c d] expanded. Cells are 100px; a full-width header/footer
    // forces row breaks, leaving partial rows the old "index +/- columns" model mishandled.
    // Layout (display indices; tiles only, header/footer carry none):
    //   row y=0   : 0(a)
    //   [header]
    //   row y=200 : 1(b) 2(c) 3(d)
    //   [footer]
    //   row y=400 : 4(e) 5(f)
    private val expandedLayout = listOf(
        TilePosition(displayIndex = 0, x = 0, y = 0),                                   // a
        TilePosition(1, x = 0, y = 200), TilePosition(2, x = 100, y = 200), TilePosition(3, x = 200, y = 200), // b c d
        TilePosition(4, x = 0, y = 400), TilePosition(5, x = 100, y = 400),             // e f
    )

    @Test fun `down from the lone first tile lands on the frame directly below, not sideways`() {
        // The reported bug: Down moved sideways (to index 1's right neighbour) because the column
        // count collapsed to 1. Geometry sends it straight down to b (index 1, same column x=0).
        assertEquals(1, pickVerticalTarget(expandedLayout, focusedIndex = 0, down = true))
    }

    @Test fun `down from a middle frame lands in the row below at the nearest column`() {
        // From c (index 2, x=100) down -> the e/f row; nearest column is f (index 5, x=100).
        assertEquals(5, pickVerticalTarget(expandedLayout, focusedIndex = 2, down = true))
        // From d (index 3, x=200) down -> e/f row has no x=200 tile; nearest is f (x=100).
        assertEquals(5, pickVerticalTarget(expandedLayout, focusedIndex = 3, down = true))
    }

    @Test fun `up from a trailing single returns into the burst frames`() {
        // From e (index 4, x=0) up -> the b/c/d row, nearest column is b (index 1, x=0).
        assertEquals(1, pickVerticalTarget(expandedLayout, focusedIndex = 4, down = false))
    }

    @Test fun `no row in the direction returns null so the caller can fall back`() {
        assertNull(pickVerticalTarget(expandedLayout, focusedIndex = 0, down = false)) // nothing above a
        assertNull(pickVerticalTarget(expandedLayout, focusedIndex = 5, down = true))  // nothing below f
        assertNull(pickVerticalTarget(expandedLayout, focusedIndex = 99, down = true)) // cursor off-screen
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
