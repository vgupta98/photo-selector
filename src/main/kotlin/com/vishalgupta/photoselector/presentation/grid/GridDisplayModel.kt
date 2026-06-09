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
