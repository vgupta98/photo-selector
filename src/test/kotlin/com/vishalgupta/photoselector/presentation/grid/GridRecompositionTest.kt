package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.RecompositionTracker
import com.vishalgupta.photoselector.presentation.designsystem.organism.PhotoThumbnail
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

/**
 * Guards the one genuinely hot recomposition path: the thumbnail tiles. The grid
 * recomputes a per-tile `isFavourite`/`isFocused` on every state change, so when
 * one tile's favourite or focus flips, every tile's item block re-runs — but
 * strong skipping must keep [PhotoThumbnail] from actually recomposing for the
 * tiles whose inputs didn't change.
 *
 * [CountedThumbnail] forwards the exact params [PhotoThumbnail] receives and
 * records in its own body, so it skips / recomposes under the same stability
 * rules the real tile does. A regression that makes tiles churn — an unstable
 * param sneaking into PhotoThumbnail, a holder losing @Immutable — flips a
 * "should skip" assertion here instead of silently costing frames in the grid.
 *
 * The harness uses a plain [FlowRow], not a LazyVerticalGrid: that isolates
 * component-level skippability from the lazy grid's own item-subcomposition
 * machinery (library-owned, recomposes items on its own schedule). The per-tile
 * inputs are computed exactly as GridScreen computes them.
 */
class GridRecompositionTest {

    @get:Rule
    val rule = createComposeRule()

    private val photos = listOf("a", "b", "c", "d").map { id ->
        Photo(
            id = PhotoId(id),
            absolutePath = Path.of("/photos/$id.jpg"),
            relativePath = "$id.jpg",
            fileName = "$id.jpg",
            sizeBytes = 1024,
            lastModifiedEpochMs = 0,
        )
    }

    private val noOpImageLoader = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? = null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    @Test
    fun togglingOneFavourite_recomposesOnlyThatTile() {
        val tracker = RecompositionTracker()
        val favourites = mutableStateOf(emptySet<PhotoId>())

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        photos = photos,
                        favourites = favourites.value,
                        focusedIndex = -1,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { favourites.value = setOf(PhotoId("b")) }
        rule.waitForIdle()

        assertEquals("b should recompose (its favourite flipped)", before["b"]!! + 1, tracker["b"])
        assertEquals("a should skip", before["a"], tracker["a"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }

    @Test
    fun filingOnePhotoIntoACategory_recomposesOnlyThatTile() {
        val tracker = RecompositionTracker()
        // Slot lists per photo, as GridScreen computes them; only "b" gains a badge.
        val badges = mutableStateOf(photos.associate { it.id.value to persistentListOf<Int>() })

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    GridWithBadges(
                        photos = photos,
                        badges = badges.value,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle {
            badges.value = badges.value.toMutableMap().apply { this["b"] = persistentListOf(1) }
        }
        rule.waitForIdle()

        assertEquals("b should recompose (gained a category badge)", before["b"]!! + 1, tracker["b"])
        assertEquals("a should skip", before["a"], tracker["a"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }

    @Test
    fun togglingOneSelection_recomposesOnlyThatTile() {
        val tracker = RecompositionTracker()
        val selection = mutableStateOf(emptySet<PhotoId>())

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        photos = photos,
                        favourites = emptySet(),
                        focusedIndex = -1,
                        loader = noOpImageLoader,
                        tracker = tracker,
                        selection = selection.value,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { selection.value = setOf(PhotoId("b")) }
        rule.waitForIdle()

        assertEquals("b should recompose (it was selected)", before["b"]!! + 1, tracker["b"])
        assertEquals("a should skip", before["a"], tracker["a"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }

    @Test
    fun movingFocus_recomposesOnlyTheTilesGainingAndLosingFocus() {
        val tracker = RecompositionTracker()
        val focusedIndex = mutableStateOf(0)

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        photos = photos,
                        favourites = emptySet(),
                        focusedIndex = focusedIndex.value,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { focusedIndex.value = 1 } // move focus a -> b
        rule.waitForIdle()

        assertEquals("a should recompose (lost focus)", before["a"]!! + 1, tracker["a"])
        assertEquals("b should recompose (gained focus)", before["b"]!! + 1, tracker["b"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }

    /**
     * Guards [GridScreen]'s `openTile` shape: the onClick is `{ openTile(index) }`, and `openTile`
     * is a resolver that captures the (unstable) tile lists. If that resolver is rebuilt every
     * recomposition (a bare val, the regression), every tile's onClick changes identity on any
     * unrelated state flip and no tile can skip. Remembering it on the tile lists - which don't
     * change on a favourite flip - keeps the onClick stable, so only the flipped tile recomposes.
     * The earlier [KeyedGrid] tests miss this because they pass a stable top-level onTileClick.
     */
    @Test
    fun togglingFavourite_withACapturedClickResolver_recomposesOnlyThatTile() {
        val tracker = RecompositionTracker()
        val favourites = mutableStateOf(emptySet<PhotoId>())
        // Stands in for GridScreen's tileFlatStart: an unstable list the resolver captures. It does
        // not change when a favourite flips, exactly as in the real grid.
        val flats = photos.indices.toList()

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    ResolvedClickGrid(
                        photos = photos,
                        favourites = favourites.value,
                        flats = flats,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { favourites.value = setOf(PhotoId("b")) }
        rule.waitForIdle()

        assertEquals("b should recompose (its favourite flipped)", before["b"]!! + 1, tracker["b"])
        assertEquals("a should skip (resolver stable -> onClick identity held)", before["a"], tracker["a"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }

    /**
     * Deleting the tail of the list (the delete path's re-emit of a shorter [photos] list) must
     * not recompose any surviving tile: every survivor keeps its index, so every per-tile input is
     * unchanged. Guards the claim that a move-to-Trash drops only the deleted tiles, not the grid.
     */
    @Test
    fun deletingLastPhoto_recomposesNoSurvivingTile() {
        val tracker = RecompositionTracker()
        val visible = mutableStateOf(photos)

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    KeyedGrid(
                        photos = visible.value,
                        focusedIndex = -1,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { visible.value = photos.dropLast(1) } // delete "d"
        rule.waitForIdle()

        assertEquals("a should skip", before["a"], tracker["a"])
        assertEquals("b should skip", before["b"], tracker["b"])
        assertEquals("c should skip", before["c"], tracker["c"])
    }

    /**
     * Deleting from the middle recomposes only the tiles whose index shifts (their index-capturing
     * click lambda changes, exactly as in GridScreen) — never the tiles ahead of the cut. This is
     * the floor the delete path can't beat: shifted positions must re-run, unchanged ones must not.
     */
    @Test
    fun deletingMiddlePhoto_recomposesOnlyTilesWhoseIndexShifted() {
        val tracker = RecompositionTracker()
        val visible = mutableStateOf(photos)

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    KeyedGrid(
                        photos = visible.value,
                        focusedIndex = -1,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { visible.value = photos.filterNot { it.id.value == "b" } } // delete "b"
        rule.waitForIdle()

        assertEquals("a should skip (index 0 unchanged)", before["a"], tracker["a"])
        assertEquals("c should recompose (index 2 -> 1)", before["c"]!! + 1, tracker["c"])
        assertEquals("d should recompose (index 3 -> 2)", before["d"]!! + 1, tracker["d"])
    }

    /**
     * Guards GridScreen's toolbar-derived lists (`categoryEntries`, `customCategories`). The screen
     * body recomposes on every cursor move (`focusedIndex`), but the top bar's inputs derive only
     * from `categories` / `memberships`. Remembered on those, each keeps its instance identity, so a
     * cursor move - which touches neither - must not recompose the top bar. The regression is
     * dropping the `remember`: a bare `categories.map { … }` mints a fresh (unstable) List every
     * pass, the bar compares it by identity, and the whole toolbar re-runs on every arrow key. This
     * test bakes in the remembered shape and asserts the bar skips, so removing the remember flips it.
     */
    @Test
    fun movingFocus_doesNotRecomposeTheToolbar() {
        val tracker = RecompositionTracker()
        val categories = listOf(
            Category.favourites(),
            Category(CategoryId("selects"), "Selects", builtIn = false),
        )
        val focusedIndex = mutableStateOf(0)

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    ToolbarHost(
                        categories = categories,
                        memberships = emptyMap(),
                        focusedIndex = focusedIndex.value,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = tracker["toolbar"]

        rule.runOnIdle { focusedIndex.value = 1 } // a cursor move - touches neither categories nor memberships
        rule.waitForIdle()

        assertEquals("toolbar should skip (its inputs are unchanged across a cursor move)", before, tracker["toolbar"])
    }

    /**
     * Toggling the library rail must NOT recompose the grid tiles. `railCollapsed` is root-level
     * chrome state that flows only into the collapse toggle (its Menu/MenuOpen icon) — never into a
     * per-tile input. So opening/closing the rail is a *layout* re-measure (the grid is `weight(1f)`
     * and resizes as the rail's width animates), not a tile recomposition. This is the inverse of the
     * intuition that the rail "should recompose the whole grid": that would be the regression. The
     * test asserts the toggle recomposes (its icon depends on the flag) while every tile skips, so
     * threading `railCollapsed` into a tile input would flip the skip assertions.
     */
    @Test
    fun togglingRailCollapsed_recomposesOnlyTheCollapseToggle() {
        val tracker = RecompositionTracker()
        val railCollapsed = mutableStateOf(false)

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    RailShellHost(
                        photos = photos,
                        railCollapsed = railCollapsed.value,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }
        val toggleBefore = tracker["toggle"]

        rule.runOnIdle { railCollapsed.value = true } // collapse the rail
        rule.waitForIdle()

        assertEquals("collapse toggle should recompose (its icon depends on railCollapsed)", toggleBefore + 1, tracker["toggle"])
        assertEquals("a should skip (railCollapsed is not a tile input)", before["a"], tracker["a"])
        assertEquals("b should skip", before["b"], tracker["b"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }
}

/**
 * Reproduces GridScreen's toolbar-input derivation: the two Lists the top bar consumes are
 * remembered on their real sources (`categories` / `memberships`), so a recomposition driven by an
 * unrelated input ([focusedIndex]) hands the bar the same instances and it skips. [CountedBar]
 * forwards those Lists into a skippable composable that records, so dropping either `remember`
 * churns its identity and recomposes the bar.
 */
@Composable
private fun ToolbarHost(
    categories: List<Category>,
    memberships: Map<CategoryId, Set<PhotoId>>,
    focusedIndex: Int,
    tracker: RecompositionTracker,
) {
    // Read focusedIndex so this host recomposes on a cursor move, exactly as GridScreen does.
    @Suppress("UNUSED_EXPRESSION")
    focusedIndex
    val categoryEntries = remember(categories, memberships) {
        categories.map { it to (memberships[it.id]?.size ?: 0) }
    }
    val customCategories = remember(categories) { categories.filter { !it.builtIn } }
    CountedBar(
        tracker = tracker,
        tag = "toolbar",
        categoryEntries = categoryEntries,
        customCategories = customCategories,
    )
}

/**
 * Reproduces GridScreen's shell wiring for `railCollapsed`: the flag flows only into the collapse
 * toggle (the GridTopBar's Menu/MenuOpen icon), never into the tiles. Reading it here makes this
 * host recompose on a toggle exactly as GridScreen's body does, while every per-tile input stays
 * railCollapsed-independent — so the tiles must skip. Threading the flag into a tile input is the
 * regression [togglingRailCollapsed_recomposesOnlyTheCollapseToggle] guards against.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RailShellHost(
    photos: List<Photo>,
    railCollapsed: Boolean,
    loader: ImageLoader,
    tracker: RecompositionTracker,
) {
    CountedToggle(tracker = tracker, tag = "toggle", railCollapsed = railCollapsed)
    FlowRow {
        photos.forEach { photo ->
            CountedThumbnail(
                tracker = tracker,
                tag = photo.id.value,
                photo = photo,
                loader = loader,
                isFavourite = false,
                isFocused = false,
            )
        }
    }
}

/** A stand-in collapse toggle: skippable, its icon depends on [railCollapsed], records each recomposition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountedToggle(
    tracker: RecompositionTracker,
    tag: String,
    railCollapsed: Boolean,
) {
    tracker.record(tag)
    Surface { FlowRowLabels(if (railCollapsed) 1 else 0) }
}

/** A stand-in top bar: skippable, takes the two derived Lists, records each recomposition. */
@Composable
private fun CountedBar(
    tracker: RecompositionTracker,
    tag: String,
    categoryEntries: List<Pair<Category, Int>>,
    customCategories: List<Category>,
) {
    tracker.record(tag)
    // Touch the params so they are real inputs the compiler must compare (not dead-code-eliminated).
    Surface { FlowRowLabels(categoryEntries.size + customCategories.size) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLabels(count: Int) {
    FlowRow { repeat(count) { Surface(Modifier.size(1.dp)) {} } }
}

/** One tile per photo, with per-tile inputs computed as in GridScreen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Grid(
    photos: List<Photo>,
    favourites: Set<PhotoId>,
    focusedIndex: Int,
    loader: ImageLoader,
    tracker: RecompositionTracker,
    selection: Set<PhotoId> = emptySet(),
) {
    FlowRow {
        photos.forEachIndexed { index, photo ->
            CountedThumbnail(
                tracker = tracker,
                tag = photo.id.value,
                photo = photo,
                loader = loader,
                isFavourite = photo.id in favourites,
                isFocused = index == focusedIndex,
                isSelected = photo.id in selection,
            )
        }
    }
}

/** As [Grid], but varying each tile's category badges — the new skippable input. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridWithBadges(
    photos: List<Photo>,
    badges: Map<String, ImmutableList<Int>>,
    loader: ImageLoader,
    tracker: RecompositionTracker,
) {
    FlowRow {
        photos.forEach { photo ->
            CountedThumbnail(
                tracker = tracker,
                tag = photo.id.value,
                photo = photo,
                loader = loader,
                isFavourite = false,
                isFocused = false,
                categoryBadges = badges[photo.id.value] ?: persistentListOf(),
            )
        }
    }
}

/**
 * As [Grid], but the click handler is resolved through a list the lambda captures - the exact shape
 * of GridScreen's `openTile` (Single -> open, Burst -> expand). The resolver is remember-keyed on the
 * captured list, so an unrelated state flip (a favourite) must not hand any tile a fresh onClick. A
 * regression that drops the remember (a bare-val resolver) rebuilds it every recomposition and churns
 * every tile - flipping the "should skip" assertions in [togglingFavourite_withACapturedClickResolver_recomposesOnlyThatTile].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolvedClickGrid(
    photos: List<Photo>,
    favourites: Set<PhotoId>,
    flats: List<Int>,
    loader: ImageLoader,
    tracker: RecompositionTracker,
) {
    val openTile: (Int) -> Unit = remember(photos, flats) { { _ -> } }
    FlowRow {
        photos.forEachIndexed { index, photo ->
            key(photo.id.value) {
                CountedThumbnail(
                    tracker = tracker,
                    tag = photo.id.value,
                    photo = photo,
                    loader = loader,
                    isFavourite = photo.id in favourites,
                    isFocused = false,
                    onClick = { openTile(index) },
                )
            }
        }
    }
}

/**
 * Records one recomposition then renders the real [PhotoThumbnail] with the same
 * params. [record] sits in this scope's body (not behind a content lambda), so it
 * ticks exactly when this skippable composable recomposes — i.e. when one of the
 * forwarded params actually changed.
 */
@Composable
private fun CountedThumbnail(
    tracker: RecompositionTracker,
    tag: String,
    photo: Photo,
    loader: ImageLoader,
    isFavourite: Boolean,
    isFocused: Boolean,
    isSelected: Boolean = false,
    categoryBadges: ImmutableList<Int> = persistentListOf(),
    onClick: () -> Unit = {},
) {
    tracker.record(tag)
    PhotoThumbnail(
        photo = photo,
        loader = loader,
        isMarked = isFavourite,
        isFocused = isFocused,
        isSelected = isSelected,
        onClick = onClick,
        onToggleSelect = {},
        onRangeSelect = {},
        modifier = Modifier.size(AppTheme.dimens.thumbnailMinCell),
        categoryBadges = categoryBadges,
    )
}

/**
 * As [Grid], but keyed by photo id with an index-capturing click lambda — the exact shape of
 * GridScreen's LazyVerticalGrid items. Keying lets a deletion preserve surviving tiles'
 * composition state (the way the lazy grid does), and the index capture means a tile whose
 * position shifts receives a fresh `onClick` and must recompose, while a tile whose index is
 * unchanged keeps an equal `onClick` and skips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyedGrid(
    photos: List<Photo>,
    focusedIndex: Int,
    loader: ImageLoader,
    tracker: RecompositionTracker,
    onTileClick: (Int) -> Unit = {},
) {
    FlowRow {
        photos.forEachIndexed { index, photo ->
            key(photo.id.value) {
                CountedThumbnail(
                    tracker = tracker,
                    tag = photo.id.value,
                    photo = photo,
                    loader = loader,
                    isFavourite = false,
                    isFocused = index == focusedIndex,
                    onClick = { onTileClick(index) },
                )
            }
        }
    }
}
