package com.vishalgupta.photoselector.presentation.grid

/**
 * The grid juggles three index spaces, and confusing two of them is the single most recurring class
 * of grid bug (see the `project_burst_grid_index_and_identity_traps` note, and the warnings in
 * `CLAUDE.md`). Two of the three are now distinct *types*, so the confusion is a compile error rather
 * than a comment future-you has to remember to obey:
 *
 *  - [FlatIndex] — an index into the flat photo list (`GridUiState.photos`, repository order). This is
 *    what the rest of the app speaks: browser / Inspect / Survey navigation and every persisted scroll
 *    position (`BrowsePosition.lastIndex`). "Never put a tile index on the nav wire."
 *  - [TileIndex] — an index into the grid's *display tiles* (`displayGroups`), where a collapsed burst
 *    is one tile and an open burst's frames are individual tiles. This is what focus, the multi-select
 *    anchor and tile clicks address. "Never put a flat index into grid focus."
 *
 * The third space — the LazyGrid's own *render-item* index, which gains non-focusable header/footer
 * rows when a burst is expanded — stays a plain `Int`: it never leaves the grid's rendering layer and
 * is only ever handed straight back to `LazyGridState`, so it can't be confused with the other two.
 *
 * Both are `@JvmInline value class`es: zero allocation, the underlying `Int` at runtime, but the
 * compiler refuses to substitute one for the other or for a bare `Int`. Conversions are deliberate and
 * grep-able — `FlatIndex(i)` / `.value` — and cluster at the grid's edges (the nav wire, persistence,
 * the LazyGrid API), which is exactly where the flat↔tile translation legitimately happens.
 */
@JvmInline
value class FlatIndex(val value: Int) {
    companion object {
        /**
         * The list-start fallback returned on a lookup miss (e.g. an empty grid) — the same `0` the
         * untyped code already fell back to. NOT an out-of-band sentinel: `FlatIndex(0)` is also the
         * real first photo, so this can't distinguish "no position" from "photo 0" (it never needed
         * to). Contrast [TileIndex.NONE], which is a genuine out-of-band `-1`.
         */
        val ZERO = FlatIndex(0)
    }
}

@JvmInline
value class TileIndex(val value: Int) {
    /** True when this addresses a real tile (not the "no focus" sentinel). */
    val isSet: Boolean get() = value >= 0

    companion object {
        /** The "no tile focused / no anchor" sentinel — the typed replacement for the old `-1`. */
        val NONE = TileIndex(-1)
    }
}

/** Tile-space list access, so hot paths read `tiles.getOrNull(focusedIndex)` without a bare `.value`. */
internal fun <T> List<T>.getOrNull(index: TileIndex): T? = getOrNull(index.value)

/** Flat-space list access, the mirror of the [TileIndex] accessor above. */
internal fun <T> List<T>.getOrNull(index: FlatIndex): T? = getOrNull(index.value)
