package com.vishalgupta.photoselector.presentation.grid

import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import kotlin.math.abs

/**
 * The grid's "display groups": the collapsed [groups] with at most one burst expanded in place.
 * The burst whose id is [expandedBurstId] is exploded into one [PhotoGroup.Single] per frame so
 * focus, selection and filing act on a single frame while it is open; every other tile is left
 * untouched. A collapsed burst stays a [PhotoGroup.Burst] (one tile, files all its frames as a
 * batch). This is the single index space the view model's focus/selection operate over and the
 * grid renders against, so the two can never drift.
 */
internal fun displayGroupsFor(groups: List<PhotoGroup>, expandedBurstId: PhotoId?): List<PhotoGroup> {
    if (expandedBurstId == null) return groups
    return groups.flatMap { group ->
        if (group is PhotoGroup.Burst && group.groupId == expandedBurstId) {
            group.photos.map(PhotoGroup::Single)
        } else {
            listOf(group)
        }
    }
}

/**
 * One entry in the rendered grid. Most are a [Tile] (a single photo, a collapsed burst, or - when a
 * burst is open - one of its frames); an expanded burst is bracketed by a full-width [BurstHeader]
 * that labels the run and offers Collapse, and a full-width [BurstFooter] that closes it (and forces
 * the following tiles onto a fresh row, so the run never bleeds into the rest of the grid). Header
 * and footer are not focusable and carry no display index, so the [Tile.displayIndex] sequence
 * lines up exactly with [displayGroupsFor]'s output.
 */
internal sealed interface GridRenderItem {
    data class Tile(
        val group: PhotoGroup,
        val displayIndex: Int,
        // True when this tile is a frame of the currently expanded burst (drives the inline accent).
        val expandedFrame: Boolean,
    ) : GridRenderItem

    data class BurstHeader(val burst: PhotoGroup.Burst) : GridRenderItem

    data class BurstFooter(val burst: PhotoGroup.Burst) : GridRenderItem
}

/**
 * Builds the renderable item list (headers + tiles) for [groups] with [expandedBurstId] open. The
 * [GridRenderItem.Tile.displayIndex] values it assigns are exactly the indices into
 * [displayGroupsFor] `(groups, expandedBurstId)` - asserted in `GridDisplayModelTest` - so the grid
 * and the view model share one index space.
 */
internal fun buildRenderItems(groups: List<PhotoGroup>, expandedBurstId: PhotoId?): List<GridRenderItem> {
    val items = ArrayList<GridRenderItem>(groups.size)
    var displayIndex = 0
    for (group in groups) {
        if (group is PhotoGroup.Burst && group.groupId == expandedBurstId) {
            items += GridRenderItem.BurstHeader(group)
            for (frame in group.photos) {
                items += GridRenderItem.Tile(PhotoGroup.Single(frame), displayIndex++, expandedFrame = true)
            }
            items += GridRenderItem.BurstFooter(group)
        } else {
            items += GridRenderItem.Tile(group, displayIndex++, expandedFrame = false)
        }
    }
    return items
}

/**
 * Resolves a LazyGrid first-visible *render-item* index to the tile ([GridRenderItem.Tile.displayIndex])
 * it represents, or null when [renderItems] is empty. The grid's items are [renderItems] - which, when a
 * burst is expanded, also hold non-focusable [GridRenderItem.BurstHeader]/[GridRenderItem.BurstFooter]
 * rows - so a render index runs 1-2 ahead of the tile (displayIndex) space. We map to the first tile at
 * or after [renderIndex] (the first visible tile content; a header sits directly above its frames, a
 * footer directly above the post-burst tiles), falling back to the nearest preceding tile when the
 * render index lands on a trailing footer with no tile after it.
 */
internal fun tileDisplayIndexForRenderItem(renderItems: List<GridRenderItem>, renderIndex: Int): Int? {
    if (renderItems.isEmpty()) return null
    val start = renderIndex.coerceIn(0, renderItems.lastIndex)
    for (i in start..renderItems.lastIndex) {
        (renderItems[i] as? GridRenderItem.Tile)?.let { return it.displayIndex }
    }
    for (i in start downTo 0) {
        (renderItems[i] as? GridRenderItem.Tile)?.let { return it.displayIndex }
    }
    return null
}

/**
 * The inverse of [tileDisplayIndexForRenderItem]: the LazyGrid *render-item* index of the
 * [GridRenderItem.Tile] carrying [displayIndex], or null if none does. Needed wherever a tile-space
 * focus index has to address the LazyGrid directly ([androidx.compose.foundation.lazy.grid.LazyGridState.animateScrollToItem],
 * [androidx.compose.foundation.lazy.grid.LazyGridItemInfo.index] matching), since those APIs speak
 * render-item space and an expanded burst's header/footer make the two diverge.
 */
internal fun renderIndexForTile(renderItems: List<GridRenderItem>, displayIndex: Int): Int? {
    val i = renderItems.indexOfFirst { it is GridRenderItem.Tile && it.displayIndex == displayIndex }
    return if (i >= 0) i else null
}

/**
 * Maps a LazyGrid first-visible *render-item* index to a FLAT photo index for persistence / return
 * anchors, bridging the render-item space (with header/footer rows) and the tile space [tileFlatStart]
 * is keyed on. Without this, an expanded burst's header/footer offset the lookup and the persisted /
 * returned position drifts by one or two tiles.
 */
internal fun flatIndexForRenderItem(
    renderItems: List<GridRenderItem>,
    tileFlatStart: List<Int>,
    renderIndex: Int,
): Int {
    val displayIndex = tileDisplayIndexForRenderItem(renderItems, renderIndex) ?: return 0
    return tileFlatStart.getOrElse(displayIndex) { 0 }
}

/** A laid-out tile's position: its [displayIndex] and the top-left pixel offset of its grid cell. */
internal data class TilePosition(val displayIndex: Int, val x: Int, val y: Int)

/**
 * Picks the up/down keyboard target among the laid-out [tiles] by geometry: the nearest tile row in
 * the chosen direction, then within it the tile closest to the cursor's column. Returns null when
 * the cursor isn't among [tiles] (scrolled off) or there is no tile row in that direction on screen
 * - the caller then falls back to a column-step so the focus effect can scroll a fresh target in.
 *
 * Geometry, not `index +/- columns`, because an expanded burst inserts full-width header/footer rows
 * and the partial rows they create, which the arithmetic model can't represent (it made Down behave
 * like Right). [tiles] must already exclude the non-focusable header/footer items.
 */
internal fun pickVerticalTarget(tiles: List<TilePosition>, focusedIndex: Int, down: Boolean): Int? {
    val current = tiles.firstOrNull { it.displayIndex == focusedIndex } ?: return null
    val inDirection = tiles.filter { if (down) it.y > current.y else it.y < current.y }
    if (inDirection.isEmpty()) return null
    val rowY = inDirection.minByOrNull { abs(it.y - current.y) }!!.y
    return inDirection.filter { it.y == rowY }.minByOrNull { abs(it.x - current.x) }!!.displayIndex
}
