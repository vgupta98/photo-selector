package com.vishalgupta.photoselector.presentation.grid

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The grid translates the FLAT photo index that the browser / Inspect / persisted
 * position speak into its own TILE index. A burst collapses several flat frames into one tile,
 * so the two index spaces diverge once any burst precedes the target - that divergence was the
 * "scroll position is messed up after going back" bug. [tileIndexForFlat] is the translator.
 */
class GridScrollRestoreTest {

    // Layout: single | burst[1..3] | single | single  -> tiles start at flat 0, 1, 4, 5.
    // a(0) | [b(1) c(2) d(3)] | e(4) | f(5)
    private val tileFlatStart = listOf(0, 1, 4, 5).map(::FlatIndex)

    @Test fun `flat index before any burst maps one-to-one`() {
        assertEquals(TileIndex(0), tileIndexForFlat(tileFlatStart, FlatIndex(0))) // a -> tile 0
    }

    @Test fun `any frame inside a burst maps to that one burst tile`() {
        assertEquals(TileIndex(1), tileIndexForFlat(tileFlatStart, FlatIndex(1))) // first frame
        assertEquals(TileIndex(1), tileIndexForFlat(tileFlatStart, FlatIndex(2))) // middle frame
        assertEquals(TileIndex(1), tileIndexForFlat(tileFlatStart, FlatIndex(3))) // last frame
    }

    @Test fun `flat indices after a burst are shifted back by the collapsed frames`() {
        // Without translation, flat 4 / 5 would scroll to tiles 4 / 5, which don't exist - the bug.
        assertEquals(TileIndex(2), tileIndexForFlat(tileFlatStart, FlatIndex(4))) // e
        assertEquals(TileIndex(3), tileIndexForFlat(tileFlatStart, FlatIndex(5))) // f
    }

    @Test fun `out-of-range and empty inputs clamp instead of throwing`() {
        assertEquals(TileIndex(3), tileIndexForFlat(tileFlatStart, FlatIndex(99))) // past the end -> last tile
        assertEquals(TileIndex(0), tileIndexForFlat(tileFlatStart, FlatIndex(-1))) // before the start -> first tile
        assertEquals(TileIndex(0), tileIndexForFlat(emptyList(), FlatIndex(5))) // no tiles -> 0
    }
}
