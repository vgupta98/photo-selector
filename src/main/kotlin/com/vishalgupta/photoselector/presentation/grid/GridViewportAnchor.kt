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
 * The grid's SINGLE programmatic owner of the viewport scroll. Two distinct programmatic intents move
 * `gridState` - "keep me on this photo across a grouping reshape" (the re-pin) and "bring the focused tile
 * on-screen" (focus-into-view) - and they used to live in two effects that fought over the scroll
 * MutatorMutex (the intermittent-jump bug). Both now route through this one holder, which arbitrates them
 * with its own state, so there is exactly one place that scrolls and the decision is unit-testable. (The
 * user and the framework remain co-writers of `gridState` through the scrollable; this owns only the
 * programmatic side and yields to a real gesture via [onUserScroll].)
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
 * arms [pendingFocusScroll]). A reshape re-anchors focus to the same photo's new index *without* arming the
 * flag, so [scrollFocusIntoView] no-ops there and the re-pin alone owns the viewport - that is why the two
 * never race. A bare index, or even the photo identity (which still shifts when a focused single is absorbed
 * into a burst), would fire on that reshape; the explicit user-intent flag is the only clean exclusion.
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
    private val coldFlatFallback: Int?,
) {
    var anchoredPhotoId: PhotoId? by mutableStateOf(initialAnchor)
        private set

    // The user has taken the viewport over (a scroll gesture, an arrow, or a scrollbar drag), so a pending
    // re-pin stands down and leaves them where they are. Cleared by [captureTop] at the next lens switch,
    // which re-reads the fresh top.
    var userOwned: Boolean by mutableStateOf(false)
        private set

    // Armed only by a real cursor move (an arrow); the next [scrollFocusIntoView] consumes it. A reshape
    // that re-anchors focus to the same photo's new index must NOT arm it, or focus-into-view fights the
    // re-pin (the intermittent jump).
    var pendingFocusScroll: Boolean by mutableStateOf(false)
        private set

    /** A wheel / two-finger / scrollbar drag: the user owns the viewport now, so a pending re-pin stands down. */
    fun onUserScroll() {
        userOwned = true
    }

    /**
     * An arrow keypress moved the cursor: the user owns the viewport (release the re-pin), and on an ACTUAL
     * focus change ([focusChanged]) arm the focus-into-view scroll. A coerced no-op at a grid edge passes
     * [focusChanged] = false so the flag can't linger and fire on a later reshape.
     */
    fun onCursorMove(focusChanged: Boolean) {
        userOwned = true
        if (focusChanged) pendingFocusScroll = true
    }

    /**
     * At a lens switch: re-read the live top into the anchor ONLY when the user has scrolled since it was
     * last set (or there is none yet), else keep the held anchor; then clear [userOwned] so the regroup
     * that follows re-pins this top until the user takes over again. Re-reading unconditionally is the
     * drift bug; the keep is the fix. [renderItems] / [tiles] are the current (post-recomposition) lists.
     */
    fun captureTop(renderItems: List<GridRenderItem>, tiles: List<PhotoGroup>) {
        if (userOwned || anchoredPhotoId == null) {
            anchoredPhotoId = tileDisplayIndexForRenderItem(renderItems, gridState.firstVisibleItemIndex)
                ?.let { tiles.getOrNull(it) }
                ?.photos?.firstOrNull()?.id
        }
        userOwned = false
    }

    /**
     * Pin the viewport to [anchoredPhotoId]'s tile after a reshape (or the cold flat fallback while no
     * identity anchor exists yet), unless the user owns the viewport. Resolves through [renderIndexForTile]
     * so it stays correct even with a burst expanded; the "already at the target" guard preserves the
     * retained pixel offset. Call from a `LaunchedEffect` keyed on the collapsed grouping.
     */
    suspend fun repin(
        renderItems: List<GridRenderItem>,
        tiles: List<PhotoGroup>,
        tileFlatStart: List<Int>,
    ) {
        if (userOwned) return
        val tile = anchoredPhotoId
            ?.let { id -> tiles.indexOfFirst { group -> group.photos.any { it.id == id } }.takeIf { it >= 0 } }
            ?: coldFlatFallback?.let { tileIndexForFlat(tileFlatStart, it) }
            ?: return
        val target = renderIndexForTile(renderItems, tile) ?: return
        if (gridState.firstVisibleItemIndex != target) gridState.scrollToItem(target)
    }

    /**
     * Bring the focused tile on-screen - but ONLY right after a real cursor move ([pendingFocusScroll]),
     * which it consumes. Called from a `LaunchedEffect` keyed on the focused tile index; a reshape changes
     * that index without arming the flag, so this no-ops there and the re-pin alone owns the viewport (the
     * two never race). [focusedIndex] is a TILE index; the LazyGrid (visibleItemsInfo.index,
     * animateScrollToItem) speaks RENDER-ITEM space, which gains header/footer rows when a burst is open -
     * so convert before addressing it, or we'd mis-detect visibility and scroll to the wrong row. Already
     * fully visible: nothing to do.
     */
    suspend fun scrollFocusIntoView(renderItems: List<GridRenderItem>, focusedIndex: Int) {
        if (!pendingFocusScroll) return
        pendingFocusScroll = false
        if (focusedIndex < 0) return
        val renderIdx = renderIndexForTile(renderItems, focusedIndex) ?: return
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
 * anchor exists.
 */
@Composable
internal fun rememberGridViewportAnchor(
    gridState: LazyGridState,
    initialAnchor: PhotoId?,
    coldFlatFallback: Int?,
): GridViewportAnchor = remember(gridState) {
    GridViewportAnchor(gridState, initialAnchor, coldFlatFallback)
}
