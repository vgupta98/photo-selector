package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId

/**
 * The grid's SINGLE owner of every programmatic viewport scroll. Two distinct intents move `gridState` -
 * "keep me on this photo across a grouping reshape" (the re-pin) and "bring the focused tile on-screen"
 * (focus-into-view) - and they used to live in two effects that fought over the scroll MutatorMutex (the
 * intermittent-jump bug). Both now flow through one [reconcile] called from one effect, which decides with
 * an explicit priority so exactly one of them runs per change: they can no longer race because the choice is
 * a single `when`, not two effects trusting a shared flag. (The user and the framework remain co-writers of
 * `gridState` through the scrollable; this owns only the programmatic side and yields to a real gesture via
 * [onUserScroll].)
 *
 * Re-pin job, learned the hard way over several bug rounds (see the
 * `project_burst_grid_index_and_identity_traps` note, Class E): a reshape renumbers the tile space, so the
 * retained render-item index now addresses a *different* photo and the grid appears to jump. The anchor is
 * the photo the user is parked on, re-read from the live viewport ONLY when the user has actually scrolled.
 * Re-deriving it on every reshape degrades it - a collapsed burst reports only its first frame, so a precise
 * anchor walks upward one step per lens switch (Off -> Time -> Similarity -> Off sliding off the start
 * photo) - and `scrollToItem` clamping near the list end defeats any "keep it if still visible" shortcut.
 * Tying the re-read to a real scroll gesture keeps a switch-only sequence pinned to one photo end to end.
 *
 * Focus-into-view job: bring the focused tile on-screen, but ONLY after a real cursor move ([onCursorMove]
 * arms [pendingFocusScroll]) - or a viewer-return resume seeds it once. A reshape re-anchors focus to the
 * same photo's new index *without* arming the
 * flag, so [reconcile] takes the re-pin branch there, never the focus one - that is why the two never race.
 * A bare index, or even the photo identity (which still shifts when a focused single is absorbed into a
 * burst), would look like a move on that reshape; the explicit user-intent flag is the only clean exclusion.
 *
 * Composition-owned (created by [rememberGridViewportAnchor]); takes the host-retained [gridState] as a
 * collaborator, since the host owns its lifecycle across navigation. State is read only inside effects, so
 * writing it never recomposes the grid.
 */
@Stable
internal class GridViewportAnchor(
    private val gridState: LazyGridState,
    // The photo to keep pinned, seeded from the cold restore (or null on a warm return / before photos
    // load). Set thereafter only by [captureTop].
    initialAnchor: PhotoId?,
    // The returned FLAT scroll index to fall back to on a cold start while no identity anchor exists yet
    // (on a real cold launch photos arrive after mount, so [initialAnchor] is null); null on a warm return.
    private val coldFlatFallback: FlatIndex?,
    // A photo to bring on-screen once, on the first [reconcile], REGARDLESS of the keyboard ring — the
    // warm-return resume ("scroll to the photo I was just looking at") and the "Show in All Photos" jump.
    // Resolved by identity, so it works for a mouse-only user with no ring at all (the old resume rode the
    // ring's focus-into-view, which silently did nothing without one). Null leaves the retained scroll put.
    revealPhotoId: PhotoId? = null,
) {
    var anchoredPhotoId: PhotoId? by mutableStateOf(initialAnchor)
        private set

    // The user has taken the viewport over (a wheel/two-finger scroll, an arrow, or a scrollbar drag) and
    // HOLDS it until the next lens switch: set by [onUserScroll] / [onCursorMove], cleared ONLY by
    // [captureTop]. While it is held, [reconcile] leaves a reshape's re-pin alone so the user stays put.
    // Named for that lifetime, not the instant - it does NOT mean "currently scrolling".
    var userHeldViewport: Boolean by mutableStateOf(false)
        private set

    // Armed by a real cursor move (an arrow); the next [reconcile] consumes it to bring the focused tile
    // on-screen. A reshape re-anchors focus without arming it, so focus-into-view never fires on a reshape
    // and can't fight the re-pin.
    var pendingFocusScroll: Boolean by mutableStateOf(false)
        private set

    // The photo to scroll on-screen once, consumed by the first [reconcile]. Carries the warm-return
    // resume and the "Show in All Photos" jump; ring-independent (resolved by identity over the tiles),
    // so it works whether or not a keyboard ring exists. Cleared after it fires.
    var pendingRevealId: PhotoId? by mutableStateOf(revealPhotoId)
        private set

    // The grouping last seen by [reconcile], compared by REFERENCE. The grid's `baseGroups` keeps its
    // instance across a focus-only change and gets a fresh one on a reshape, so "instance changed" is
    // exactly "a reshape happened" - the gate that stops a bare focus change from triggering a re-pin.
    private var lastReconciledGroups: List<PhotoGroup>? = null

    /** A wheel / two-finger / scrollbar drag: the user holds the viewport now, so a reshape's re-pin stands down. */
    fun onUserScroll() {
        userHeldViewport = true
    }

    /**
     * An arrow keypress moved the cursor: the user holds the viewport (a reshape's re-pin stands down), and
     * on an ACTUAL focus change ([focusChanged]) arm the focus-into-view scroll. A coerced no-op at a grid
     * edge passes [focusChanged] = false so the flag can't linger and fire on a later reshape.
     */
    fun onCursorMove(focusChanged: Boolean) {
        userHeldViewport = true
        if (focusChanged) pendingFocusScroll = true
    }

    /**
     * At a lens switch: re-read the live top into the anchor ONLY when the user has scrolled since it was
     * last set (or there is none yet), else keep the held anchor; then clear [userHeldViewport] so the
     * regroup that follows re-pins this top until the user takes over again. Re-reading unconditionally is
     * the drift bug; the keep is the fix. [renderItems] / [tiles] are the current (post-recomposition) lists.
     */
    fun captureTop(renderItems: List<GridRenderItem>, tiles: List<PhotoGroup>) {
        if (userHeldViewport || anchoredPhotoId == null) {
            anchoredPhotoId = tileDisplayIndexForRenderItem(renderItems, gridState.firstVisibleItemIndex)
                ?.let { tiles.getOrNull(it) }
                ?.photos?.firstOrNull()?.id
        }
        userHeldViewport = false
    }

    /**
     * The single decision point for every programmatic RESHAPE/CURSOR viewport move. Call from ONE
     * `LaunchedEffect` keyed on BOTH the collapsed [groups] (a reshape) and the focused tile (a cursor
     * move). Exactly one thing happens per call, in priority order:
     *  - a real cursor move scrolls the focused tile on-screen (it wins over a coincident reshape, since the
     *    user's explicit move should land where they navigated), consuming [pendingFocusScroll];
     *  - otherwise a reshape ([groups] is a new instance) re-pins the anchored photo;
     *  - a bare focus change with nothing armed does nothing.
     * Because the two intents are branches of one `when`, they can never both scroll in the same pass.
     *
     * The one-shot REVEAL ([scrollRevealIntoView]) is deliberately NOT here: it runs from its own mount
     * effect so seating the ring on a "Show in All Photos" jump (which flips focusedIndex, re-keying this
     * effect) can't cancel a reveal scroll mid-animation.
     */
    suspend fun reconcile(
        renderItems: List<GridRenderItem>,
        tiles: List<PhotoGroup>,
        tileFlatStart: List<FlatIndex>,
        focusedIndex: TileIndex,
        groups: List<PhotoGroup>,
    ) {
        val reshaped = groups !== lastReconciledGroups
        lastReconciledGroups = groups
        when {
            pendingFocusScroll -> {
                pendingFocusScroll = false
                scrollTileIntoView(renderItems, focusedIndex)
            }
            reshaped -> repinToAnchor(renderItems, tiles, tileFlatStart)
        }
    }

    /**
     * One-shot reveal: bring [pendingRevealId]'s tile on-screen by identity, then clear it. Ring-independent
     * (it never touches focus), so it resumes a pure-mouse user and powers the "Show in All Photos" jump.
     * Runs from its OWN mount effect keyed on Unit (see GridScreen), NOT [reconcile] — that effect is keyed
     * on focusedIndex, and the jump seats the ring on arrival, so a reveal living there would be cancelled
     * the instant the ring moves. A reveal target outside the current slice (tile < 0) just clears.
     */
    suspend fun scrollRevealIntoView(renderItems: List<GridRenderItem>, tiles: List<PhotoGroup>) {
        val id = pendingRevealId ?: return
        pendingRevealId = null
        val tile = tiles.indexOfFirst { group -> group.photos.any { it.id == id } }
        if (tile >= 0) scrollTileIntoView(renderItems, TileIndex(tile))
    }

    /**
     * Pin the viewport to [anchoredPhotoId]'s tile (or the cold flat fallback while no identity anchor exists
     * yet), unless the user holds the viewport. Resolves through [renderIndexForTile] so it stays correct
     * even with a burst expanded; the "already at the target" guard preserves the retained pixel offset.
     */
    private suspend fun repinToAnchor(
        renderItems: List<GridRenderItem>,
        tiles: List<PhotoGroup>,
        tileFlatStart: List<FlatIndex>,
    ) {
        if (userHeldViewport) return
        val tile: TileIndex = anchoredPhotoId
            ?.let { id ->
                tiles.indexOfFirst { group -> group.photos.any { it.id == id } }.takeIf { it >= 0 }?.let(::TileIndex)
            }
            ?: coldFlatFallback?.let { tileIndexForFlat(tileFlatStart, it) }
            ?: return
        val target = renderIndexForTile(renderItems, tile) ?: return
        if (gridState.firstVisibleItemIndex != target) gridState.scrollToItem(target)
    }

    /**
     * Bring [tileIndex] on-screen if it isn't already (the focused tile after a cursor move, or a reveal
     * target by identity). [tileIndex] is a TILE index; the LazyGrid (visibleItemsInfo.index,
     * animateScrollToItem) speaks RENDER-ITEM space, which gains header/footer rows when a burst is open -
     * so convert before addressing it, or we'd mis-detect visibility and scroll to the wrong row.
     */
    private suspend fun scrollTileIntoView(renderItems: List<GridRenderItem>, tileIndex: TileIndex) {
        if (!tileIndex.isSet) return
        val renderIdx = renderIndexForTile(renderItems, tileIndex) ?: return
        val layout = gridState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == renderIdx }
        val isFullyVisible = item != null &&
            item.offset.y >= layout.viewportStartOffset &&
            item.offset.y + item.size.height <= layout.viewportEndOffset
        if (!isFullyVisible) gridState.animateScrollToItem(renderIdx)
    }
}

/**
 * Remembers a [GridViewportAnchor] for the host-retained [gridState]. Keyed on [gridState] so a fresh
 * screen mount (which re-runs composition) rebuilds it with this visit's seed, while a recomposition of
 * the same mount keeps it. [initialAnchor] is the cold identity seed (null on a warm return or before
 * photos load); [coldFlatFallback] is the returned flat index a cold start re-pins to until an identity
 * anchor exists; [revealPhotoId] is the photo to scroll on-screen once on this visit, ring-independent.
 */
@Composable
internal fun rememberGridViewportAnchor(
    gridState: LazyGridState,
    initialAnchor: PhotoId?,
    coldFlatFallback: FlatIndex?,
    revealPhotoId: PhotoId? = null,
): GridViewportAnchor = remember(gridState) {
    GridViewportAnchor(gridState, initialAnchor, coldFlatFallback, revealPhotoId)
}
