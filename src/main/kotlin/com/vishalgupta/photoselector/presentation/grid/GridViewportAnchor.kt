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
 * Owns the grid's viewport re-pin across a grouping RESHAPE - the cold settle (singles emitted first,
 * bursts collapsed a frame later) and a toolbar lens switch. A reshape renumbers the tile space, so the
 * retained render-item index now addresses a *different* photo and the grid appears to jump; this holder
 * keeps the viewport on one photo by IDENTITY across the reshape, landing on whatever tile now holds it.
 *
 * The whole job is one rule, learned the hard way over several bug rounds (see the
 * `project_burst_grid_index_and_identity_traps` note, Class E): the anchor is the photo the user is
 * parked on, and it is re-read from the live viewport ONLY when the user has actually scrolled. Re-deriving
 * it on every reshape degrades it - a collapsed burst reports only its first frame, so a precise anchor
 * walks upward one step per lens switch (Off -> Time -> Similarity -> Off sliding off the start photo) -
 * and `scrollToItem` clamping near the list end defeats any "keep it if still visible" shortcut. Tying the
 * re-read to a real scroll gesture is what keeps a switch-only sequence pinned to one photo end to end.
 *
 * Collapses what used to be three coordinating flags (`anchorPhotoId` / `userScrolled` / `reanchorArmed`)
 * into one cohesive, unit-testable unit (`reanchorArmed` was redundant: a warm mount already no-ops the
 * re-pin via a null anchor and a null fallback). Composition-owned (created by [rememberGridViewportAnchor]);
 * takes the host-retained [gridState] as a collaborator, since the host owns its lifecycle across
 * navigation. State is read only inside effects, so writing it never recomposes the grid.
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

    /** A real scroll gesture / arrow move / scrollbar drag: the user owns the viewport now, so don't re-pin. */
    fun onUserScroll() {
        userOwned = true
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
