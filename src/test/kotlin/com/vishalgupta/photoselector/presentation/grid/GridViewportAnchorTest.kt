package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Pure (no Compose rule) unit tests for [GridViewportAnchor]'s anchor-selection rule - the logic that
 * keeps a lens-switch sequence pinned to one photo. `repin`'s actual scroll needs a live layout and is
 * covered by the Compose-driven `GridKeyboardTest`; here we pin the decision the holder makes about
 * WHICH photo stays the anchor, which is where every drift bug lived.
 */
class GridViewportAnchorTest {

    private fun photo(id: String) = Photo(
        id = PhotoId(id),
        absolutePath = Path.of("/p/$id.jpg"),
        relativePath = "$id.jpg",
        fileName = "$id.jpg",
        sizeBytes = 1,
        lastModifiedEpochMs = 0,
    )

    // Layout: p0..p4 singles, then a burst [p5 p6 p7], then p8, p9. Tile 5 is the burst.
    private val groups: List<PhotoGroup> = buildList {
        addAll((0..4).map { PhotoGroup.Single(photo("p$it")) })
        add(PhotoGroup.Burst((5..7).map { photo("p$it") }))
        add(PhotoGroup.Single(photo("p8")))
        add(PhotoGroup.Single(photo("p9")))
    }
    private val renderItems = buildRenderItems(groups, expandedBurstId = null)
    private val tiles = displayGroupsFor(groups, expandedBurstId = null)

    private fun anchorAt(topIndex: Int, initial: PhotoId?) = GridViewportAnchor(
        gridState = LazyGridState(firstVisibleItemIndex = topIndex),
        initialAnchor = initial,
        coldFlatFallback = null,
    )

    @Test fun `onUserScroll marks the viewport user-held`() {
        val anchor = anchorAt(topIndex = 0, initial = null)
        assertTrue(!anchor.userHeldViewport)
        anchor.onUserScroll()
        assertTrue("a real scroll gesture takes the viewport over", anchor.userHeldViewport)
    }

    @Test fun `captureTop with no anchor reads the top tile's first photo`() {
        val anchor = anchorAt(topIndex = 5, initial = null) // top is the burst tile
        anchor.captureTop(renderItems, tiles)
        assertEquals(PhotoId("p5"), anchor.anchoredPhotoId)
    }

    @Test fun `captureTop keeps an existing anchor when the user has not scrolled`() {
        // The cursor is parked on p6 (a non-first frame of the burst now at the top). A lens switch must
        // NOT re-read the top to p5 - that burst-first-frame degradation is the multi-switch drift.
        val anchor = anchorAt(topIndex = 5, initial = PhotoId("p6"))
        anchor.captureTop(renderItems, tiles)
        assertEquals("a switch with no user scroll keeps the precise anchor", PhotoId("p6"), anchor.anchoredPhotoId)
    }

    @Test fun `captureTop re-reads the top after the user scrolled, and clears the hold`() {
        val anchor = anchorAt(topIndex = 2, initial = PhotoId("p6")) // user scrolled up to single p2
        anchor.onUserScroll()
        anchor.captureTop(renderItems, tiles)
        assertEquals("after a user scroll the anchor follows the new top", PhotoId("p2"), anchor.anchoredPhotoId)
        assertTrue("capture clears the hold so the regroup re-pins", !anchor.userHeldViewport)
    }

    @Test fun `captureTop on an empty top resolves to null rather than throwing`() {
        val anchor = GridViewportAnchor(
            gridState = LazyGridState(firstVisibleItemIndex = 0),
            initialAnchor = null,
            coldFlatFallback = null,
        )
        anchor.onUserScroll() // force the re-read branch
        anchor.captureTop(renderItems = emptyList(), tiles = emptyList())
        assertNull(anchor.anchoredPhotoId)
    }

    @Test fun `onCursorMove takes the viewport over and arms focus-scroll only on a real change`() {
        val anchor = anchorAt(topIndex = 0, initial = null)
        // A coerced no-op at a grid edge: the user is still driving, but nothing should arm the scroll.
        anchor.onCursorMove(focusChanged = false)
        assertTrue("any arrow counts as the user taking the viewport over", anchor.userHeldViewport)
        assertTrue("a no-op edge move must not arm the focus scroll", !anchor.pendingFocusScroll)
        // An actual move arms focus-into-view.
        anchor.onCursorMove(focusChanged = true)
        assertTrue("an actual cursor move arms the focus-into-view scroll", anchor.pendingFocusScroll)
    }

    @Test fun `scrollRevealIntoView consumes the pending reveal and leaves focus alone`() = runBlocking {
        // Reveal a photo that is NOT in the tiles, so the one-shot runs and consumes the flag without
        // needing a live layout to scroll. The reveal lives outside reconcile (it must not be cancelled by
        // a focus change), and it never touches focus - so a separately-armed focus scroll survives it.
        val anchor = GridViewportAnchor(
            gridState = LazyGridState(firstVisibleItemIndex = 0),
            initialAnchor = null,
            coldFlatFallback = null,
            revealPhotoId = PhotoId("absent"),
        )
        assertEquals(PhotoId("absent"), anchor.pendingRevealId)
        anchor.onCursorMove(focusChanged = true)
        anchor.scrollRevealIntoView(renderItems, tiles)
        assertNull("the reveal is consumed", anchor.pendingRevealId)
        assertTrue("the reveal never touches focus; an armed focus scroll survives", anchor.pendingFocusScroll)
    }

    @Test fun `reconcile consumes the focus arm only when a cursor move set it`() = runBlocking {
        val anchor = anchorAt(topIndex = 0, initial = null)
        // First reconcile is the initial reshape (lastReconciledGroups is null) with no anchor and nothing
        // armed: it takes the re-pin branch, which no-ops on a null anchor - so it neither scrolls nor arms.
        anchor.reconcile(renderItems, tiles, emptyList(), focusedIndex = TileIndex(3), groups = groups)
        assertTrue("an unarmed reconcile does not arm a focus scroll", !anchor.pendingFocusScroll)
        // Arm via a real cursor move, then reconcile on the SAME grouping instance (no reshape): the focus
        // branch runs and consumes the flag; focus is unset so it returns before any real scroll.
        anchor.onCursorMove(focusChanged = true)
        anchor.reconcile(renderItems, tiles, emptyList(), focusedIndex = TileIndex(-1), groups = groups)
        assertTrue("the pending focus flag is consumed", !anchor.pendingFocusScroll)
    }
}
