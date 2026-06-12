package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.common.CategoryToggle
import com.vishalgupta.photoselector.presentation.common.GroupingMode
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import com.vishalgupta.photoselector.presentation.common.customCategories
import com.vishalgupta.photoselector.presentation.common.digitSlot
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BurstExpandedFooter
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BurstExpandedHeader
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BusyBar
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ConfirmDialog
import com.vishalgupta.photoselector.presentation.designsystem.molecule.EmptyState
import com.vishalgupta.photoselector.presentation.designsystem.molecule.GridKeyboardLegend
import com.vishalgupta.photoselector.presentation.designsystem.molecule.KeyHint
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToast
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToastDefaults
import com.vishalgupta.photoselector.presentation.designsystem.organism.GridSelectionTopBar
import com.vishalgupta.photoselector.presentation.designsystem.organism.GridTopBar
import com.vishalgupta.photoselector.presentation.designsystem.organism.PhotoThumbnail
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.navigation.MAX_SURVEY_PHOTOS
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun GridScreen(
    viewModel: GridViewModel,
    initialScrollIndex: Int,
    onTileClick: (index: Int) -> Unit,
    onChangeFolder: () -> Unit,
    onSelectCategory: (currentScrollIndex: Int, id: CategoryId) -> Unit,
    onCompareSelection: (indices: List<Int>, returnScrollIndex: Int) -> Unit,
    onBack: (() -> Unit)?,
    // The scroll state retained for this (root, scope) across the session, supplied by the host so it
    // survives a Grid -> Browser -> Grid round trip. [anchorInitialScroll] is true only on the first
    // (cold) visit, where [initialScrollIndex] still needs to be applied as grouping settles; on a
    // warm return the retained state already holds the exact position, so re-anchoring is skipped.
    // The view model is retained too (it is NOT cleared here on navigate-away — the host evicts it on
    // a root change), so the grid returns with its groups, focus and scroll intact.
    gridState: LazyGridState,
    anchorInitialScroll: Boolean,
) {
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Surface the one-shot toggle confirmation as a transient pill (mirrors the browser).
    var categoryToast by remember { mutableStateOf<CategoryToggle?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.toggleEvents.collectLatest { event ->
            categoryToast = event
            delay(1200)
            categoryToast = null
        }
    }

    GridScreen(
        state = state,
        initialScrollIndex = initialScrollIndex,
        retainedGridState = gridState,
        anchorInitialScroll = anchorInitialScroll,
        categoryToast = categoryToast,
        onTileClick = onTileClick,
        onChangeFolder = onChangeFolder,
        onSelectCategory = onSelectCategory,
        onCompareSelection = onCompareSelection,
        onSelectionTooLargeToCompare = viewModel::notifySurveyCapExceeded,
        onCreateCategory = viewModel::createCategory,
        onRenameCategory = viewModel::renameCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onBack = onBack,
        onSetFocusedIndex = viewModel::setFocusedIndex,
        onToggleMembershipAtFocus = viewModel::toggleMembershipAtFocus,
        onToggleCustomCategoryAtFocus = viewModel::toggleCustomCategoryAtFocus,
        onExportTxt = {
            coroutineScope.launch {
                val target = NativeFileDialogs.pickSaveFile(
                    title = "Export list",
                    defaultName = "photos.txt",
                ) ?: return@launch
                viewModel.exportTxt(target)
            }
        },
        onCopyToFolder = { policy ->
            coroutineScope.launch {
                val dir = NativeFileDialogs.pickDirectory("Choose destination folder")
                    ?: return@launch
                viewModel.copyTo(dir, policy)
            }
        },
        onDismissToast = viewModel::dismissToast,
        onFirstVisibleItemChanged = viewModel::onFirstVisibleItemChanged,
        onSelectGroupingMode = viewModel::setGroupingMode,
        onToggleBurstExpansion = viewModel::toggleBurstExpansion,
        onCollapseBurst = viewModel::collapseBurst,
        imageLoader = viewModel.imageLoader,
        onToggleSelection = viewModel::toggleSelection,
        onSelectRange = viewModel::selectRange,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onFileSelectionIntoFavourites = viewModel::fileSelectionIntoFavourites,
        onFileSelectionIntoCustom = viewModel::fileSelectionIntoCustom,
        onDeleteSelection = viewModel::deleteSelection,
        onCopySelection = { policy ->
            coroutineScope.launch {
                val dir = NativeFileDialogs.pickDirectory("Copy selected photos to…")
                    ?: return@launch
                viewModel.copySelectionTo(dir, policy)
            }
        },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GridScreen(
    state: GridUiState,
    initialScrollIndex: Int,
    // The host-retained scroll state for this (root, scope), or null for the stateless hosting (tests,
    // previews) that owns no retention — then the grid makes its own, seeded from [initialScrollIndex].
    retainedGridState: LazyGridState? = null,
    // True only on a cold first visit, where [initialScrollIndex] is re-anchored as grouping settles;
    // false on a warm return, where [retainedGridState] already holds the exact position.
    anchorInitialScroll: Boolean = true,
    onTileClick: (index: Int) -> Unit,
    onChangeFolder: () -> Unit,
    onSelectCategory: (currentScrollIndex: Int, id: CategoryId) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (CategoryId, String) -> Unit,
    onDeleteCategory: (CategoryId) -> Unit,
    onBack: (() -> Unit)?,
    onSetFocusedIndex: (Int) -> Unit,
    onToggleMembershipAtFocus: () -> Unit,
    onToggleCustomCategoryAtFocus: (slot: Int) -> Unit,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    onDismissToast: () -> Unit,
    onFirstVisibleItemChanged: (Int) -> Unit = {},
    onSelectGroupingMode: (GroupingMode) -> Unit = {},
    onToggleBurstExpansion: (PhotoId) -> Unit = {},
    onCollapseBurst: () -> Unit = {},
    imageLoader: ImageLoader,
    categoryToast: CategoryToggle? = null,
    // Multi-select plumbing. Defaulted so the stateless screen can be hosted (tests, previews)
    // without wiring selection — a grid with no selection handlers simply never selects.
    onToggleSelection: (Int) -> Unit = {},
    onSelectRange: (Int) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onFileSelectionIntoFavourites: () -> Unit = {},
    onFileSelectionIntoCustom: (slot: Int) -> Unit = {},
    onDeleteSelection: () -> Unit = {},
    onCopySelection: (ConflictPolicy) -> Unit = {},
    onCompareSelection: (indices: List<Int>, returnScrollIndex: Int) -> Unit = { _, _ -> },
    onSelectionTooLargeToCompare: () -> Unit = {},
    // The scrollbar's drag interactions, hoisted so a test can drive a scrollbar-drag-during-settle
    // (emit DragInteraction.Start) without a real thin-scrollbar gesture; production uses the default.
    scrollbarInteraction: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier,
) {
    // The collapsed grouping. The view model populates [GridUiState.groups] (singles, then bursts
    // once grouping resolves); a state with no groups computed yet (e.g. a test or a static preview)
    // falls back to one tile per photo so the grid still renders and navigates.
    val baseGroups = remember(state.groups, state.photos) {
        state.groups.ifEmpty { state.photos.map(PhotoGroup::Single) }
    }
    // The renderable items: headers + tiles, with the expanded burst (if any) unfolded in place.
    val renderItems = remember(baseGroups, state.expandedBurstId) {
        buildRenderItems(baseGroups, state.expandedBurstId)
    }
    // The tiles-only index space focus / selection / clicks address, in lock-step with the view
    // model's [GridUiState.displayGroups]. A collapsed burst is one tile; an open burst's frames are
    // individual tiles.
    val tiles = remember(baseGroups, state.expandedBurstId) {
        displayGroupsFor(baseGroups, state.expandedBurstId)
    }
    // Flat index in [state.photos] where each display tile's frames begin - the bridge between the
    // flat photo index the rest of the app speaks (browser, Compare, Survey, persisted scroll) and
    // the grid's own tile index.
    val tileFlatStart = remember(tiles) {
        var acc = 0
        tiles.map { group -> acc.also { acc += group.photos.size } }
    }
    // initialScrollIndex is a FLAT photo index (which photo to reveal); convert to its tile, since a
    // burst makes the two diverge. Grouping settles asynchronously, so a later effect re-anchors.
    // Prefer the host-retained scroll state (so a warm return keeps the exact position); fall back to
    // a self-owned one seeded from initialScrollIndex for the stateless hosting that has no retention.
    val ownGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = tileIndexForFlat(tileFlatStart, initialScrollIndex),
    )
    val gridState = retainedGridState ?: ownGridState
    // What we hand back to the browser / Compare / Survey / persistence is a FLAT photo index, so no
    // caller needs to know tiles exist. firstVisibleItemIndex is a renderItems index (header/footer
    // included when a burst is open), so map it through the tile space before the flat lookup.
    fun firstVisibleFlat() = flatIndexForRenderItem(renderItems, tileFlatStart, gridState.firstVisibleItemIndex)
    val focusRequester = remember { FocusRequester() }
    // Open/closed state of the destructive delete confirmation. The Delete button and Cmd+Delete
    // both arm it; confirming calls [onDeleteSelection], which performs the move to Trash.
    var confirmingDelete by remember { mutableStateOf(false) }

    val currentCategory: Category? = (state.scope as? CategoryScope.Category)
        ?.let { sc -> state.categories.firstOrNull { it.id == sc.id } }
    val categoryEntries = state.categories.map { it to (state.memberships[it.id]?.size ?: 0) }

    // Custom categories in slot order — drives both the per-tile numbered badges and the
    // 1..9 digit mapping, so a tile's "2" chip always matches the key that toggled it.
    val customCategories = state.categories.customCategories()

    // Opening a tile: a collapsed burst unfolds in place into its frames; a single photo - including
    // an open burst's frame - opens the browser at that photo. There is no frame-count cap: even a
    // huge burst is culled inline. (Compare / Survey stay reachable by selecting frames and pressing
    // C, exactly as for any multi-select.)
    // Remembered, not a bare val: this resolver captures the unstable tiles / tileFlatStart lists, so
    // rebuilding it every recomposition would hand every tile a fresh { openTile(index) } onClick and
    // defeat per-tile skipping on any unrelated state flip (favourite, focus, selection). tiles and
    // tileFlatStart only change on a regroup/expansion, which is exactly when the resolver *should*
    // be rebuilt - so key on them (and the two stable callbacks) and the onClick identity holds steady
    // through the hot path.
    val openTile: (Int) -> Unit = remember(tiles, tileFlatStart, onTileClick, onToggleBurstExpansion) {
        open@{ tileIndex ->
            val group = tiles.getOrNull(tileIndex) ?: return@open
            when (group) {
                is PhotoGroup.Single -> onTileClick(tileFlatStart[tileIndex])
                is PhotoGroup.Burst -> onToggleBurstExpansion(group.groupId)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(TOAST_DURATION_MS)
            onDismissToast()
        }
    }

    // The viewport re-pin across a grouping reshape - the cold settle and a lens switch - lives in one
    // holder (see [GridViewportAnchor]). It keeps the viewport on one photo by IDENTITY, re-read from the
    // live top only when the user has actually scrolled (re-deriving it every reshape degrades a collapsed
    // burst to its first frame and walks the viewport upward across switches). Cold seed: the returned
    // photo's id *if the photos are already loaded* - on a real cold launch they arrive after mount, so
    // this is null and [coldFlatFallback] (the flat index) carries the restore until an identity anchor
    // exists; on a warm return both are null so the retained pixel position is left untouched.
    // On a warm return from the viewer the view model has re-seated an existing ring onto the photo the
    // browser left on (GridViewModel.setLastViewed). If that photo is off-screen, resume there: seed the
    // anchor's focus-into-view so the first reconcile scrolls the ring on-screen. Evaluated once at mount
    // and gated so it can't fire on a cold first visit, with no ring, or on a stale ring that is NOT on the
    // last-viewed photo (the warm-return-keeps-scroll case) - only a ring that tracks the returned photo.
    val resumeFocusIntoView = remember {
        !anchorInitialScroll &&
            state.focusedIndex >= 0 &&
            tiles.getOrNull(state.focusedIndex)?.photos?.any { it.id == state.lastViewedPhotoId } == true
    }
    val anchor = rememberGridViewportAnchor(
        gridState = gridState,
        initialAnchor = if (anchorInitialScroll) state.photos.getOrNull(initialScrollIndex)?.id else null,
        coldFlatFallback = initialScrollIndex.takeIf { anchorInitialScroll },
        resumeFocusIntoView = resumeFocusIntoView,
    )
    // Moves the keyboard cursor: tells the anchor the user took the viewport over (releases the re-pin) and,
    // on an actual change, arms the focus-into-view scroll. The no-op-at-edge guard lives here because the
    // change is relative to the current focus; the holder owns the flag and the scroll itself.
    val moveFocus: (Int) -> Unit = { target ->
        anchor.onCursorMove(focusChanged = target != state.focusedIndex)
        onSetFocusedIndex(target)
    }
    // A scrollbar drag reaches [gridState] through the scrollable, NOT the Column's pointer modifier
    // below, so observe the scrollbar's own drag interactions to release the re-pin too (the gap that
    // used to let a scrollbar-drag-during-settle get yanked back). Distinct from gridState.isScrollInProgress,
    // which a programmatic re-pin also trips - this fires only on a real user drag.
    LaunchedEffect(scrollbarInteraction) {
        scrollbarInteraction.interactions.collect { if (it is DragInteraction.Start) anchor.onUserScroll() }
    }

    // Persisted scroll position is a FLAT photo index - the 50k-photo resume point, written on every
    // scroll. The subscription is start-once (gridState is stable), but renderItems / tileFlatStart
    // change on every regroup/expansion - so read the latest via rememberUpdatedState rather than
    // capture a stale snapshot that would persist a wrong position after a burst expands.
    val latestRenderItems by rememberUpdatedState(renderItems)
    val latestTileFlatStart by rememberUpdatedState(tileFlatStart)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { index -> onFirstVisibleItemChanged(flatIndexForRenderItem(latestRenderItems, latestTileFlatStart, index)) }
    }

    // Every programmatic viewport move goes through the anchor's single [reconcile], keyed on BOTH the
    // collapsed grouping (a reshape) and the focused tile (a cursor move) so it is the lone decision point:
    // exactly one of re-pin / focus-into-view runs per change, and the two can no longer race. Keyed on
    // baseGroups (not the display tiles), so merely expanding a burst - which changes neither key - doesn't
    // reconcile; that case is left to the LazyGrid's own key retention.
    LaunchedEffect(baseGroups, state.focusedIndex) {
        anchor.reconcile(renderItems, tiles, tileFlatStart, state.focusedIndex, baseGroups)
    }

    // A toolbar lens switch reshapes the whole grid: capture the photo at the live top before the reshape,
    // then re-pin to it after. Remembered (not a bare lambda), keyed on the unstable renderItems / tiles it
    // reads plus the wrapped callback, so the toolbar keeps skipping through the hot path - like [openTile].
    val onSelectGroupingModeAnchored: (GroupingMode) -> Unit =
        remember(renderItems, tiles, onSelectGroupingMode) {
            { mode ->
                anchor.captureTop(renderItems, tiles)
                onSelectGroupingMode(mode)
            }
        }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            // A two-finger / wheel scroll (the app's scroll gestures) releases the re-pin: a programmatic
            // scroll or a grouping reshape doesn't emit this, so neither is mistaken for the user taking
            // over. (Scrollbar drags reach the grid through the scrollable, not here - those are caught via
            // the scrollbar's interactionSource above.)
            .onPointerEvent(PointerEventType.Scroll) { anchor.onUserScroll() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    // A tile's `Modifier.clickable` activates on the Enter / Space KEY-UP, and a mouse
                    // click leaves Compose focus on that tile - which differs from our keyboard cursor.
                    // If we let the key-up through, the focused tile re-fires its onClick and re-opens
                    // the mouse-clicked tile, undoing the cursor's action: the "Enter always re-expands
                    // the burst I opened with the mouse" bug. The grid owns Enter/Space (handled on the
                    // key-down below), so swallow their key-ups too and the tile clickable never sees them.
                    return@onPreviewKeyEvent event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.Spacebar
                }
                val meta = event.isMetaPressed
                val hasSelection = state.hasSelection
                // Keyboard focus moves over tiles (groups), not the flat photo list — a collapsed
                // burst is one stop.
                val maxIndex = tiles.size - 1
                // Cmd+A arms a multi-select over the whole scope.
                if (meta && event.key == Key.A) {
                    if (maxIndex >= 0) onSelectAll()
                    return@onPreviewKeyEvent true
                }
                // Cmd+Delete (Cmd+Backspace on a Mac keyboard) over a selection arms the move-to-
                // Trash confirmation — the macOS "move to trash" chord, applied to the whole pick.
                if (meta && (event.key == Key.Backspace || event.key == Key.Delete) && hasSelection) {
                    confirmingDelete = true
                    return@onPreviewKeyEvent true
                }
                // C opens the multi-selection side by side: 2 tiles -> Compare, 3+ -> Survey. The
                // indices are taken in scope (reading) order; only fires with a 2+ selection. Past
                // the cap it declines with a toast instead of opening an unusable wall of tiles.
                if (!meta && event.key == Key.C && state.selection.size >= 2) {
                    if (state.selection.size <= MAX_SURVEY_PHOTOS) {
                        val indices = state.photos.indices.filter { state.photos[it].id in state.selection }
                        onCompareSelection(indices, firstVisibleFlat())
                    } else {
                        onSelectionTooLargeToCompare()
                    }
                    return@onPreviewKeyEvent true
                }
                val isArrow = event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown
                if (isArrow && state.focusedIndex < 0 && maxIndex >= 0) {
                    // Seed focus on the first visible TILE. firstVisibleItemIndex is render-item space
                    // (header/footer included when a burst is open), so map it into tile space first.
                    val seed = tileDisplayIndexForRenderItem(renderItems, gridState.firstVisibleItemIndex) ?: 0
                    moveFocus(seed.coerceIn(0, maxIndex))
                    return@onPreviewKeyEvent true
                }
                // Bare 1..9 files into the Nth custom category: the whole selection when one is
                // armed, otherwise just the focused tile (the keyboard path for filing from All
                // Photos without leaving it).
                val slot = if (meta) null else digitSlot(event.key)
                if (slot != null) {
                    if (hasSelection) {
                        onFileSelectionIntoCustom(slot)
                    } else if (state.focusedIndex in 0..maxIndex) {
                        onToggleCustomCategoryAtFocus(slot)
                    }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        moveFocus((state.focusedIndex - 1).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight -> {
                        moveFocus((state.focusedIndex + 1).coerceAtMost(maxIndex))
                        true
                    }
                    Key.DirectionUp -> {
                        moveFocus(verticalNavTarget(gridState, renderItems, state.focusedIndex, maxIndex, down = false))
                        true
                    }
                    Key.DirectionDown -> {
                        moveFocus(verticalNavTarget(gridState, renderItems, state.focusedIndex, maxIndex, down = true))
                        true
                    }
                    Key.Enter -> {
                        if (state.focusedIndex in 0..maxIndex) {
                            openTile(state.focusedIndex)
                        }
                        true
                    }
                    Key.F -> if (meta) false else {
                        if (hasSelection) onFileSelectionIntoFavourites() else onToggleMembershipAtFocus()
                        true
                    }
                    Key.Spacebar -> if (meta) false else {
                        if (hasSelection) onFileSelectionIntoFavourites() else onToggleMembershipAtFocus()
                        true
                    }
                    // Esc peels one layer at a time: clear a selection, then fold an open burst,
                    // then pop the screen. Each press undoes the most recent thing the user did.
                    Key.Escape -> when {
                        hasSelection -> {
                            onClearSelection()
                            true
                        }
                        state.expandedBurstId != null -> {
                            onCollapseBurst()
                            true
                        }
                        onBack != null -> {
                            onBack()
                            true
                        }
                        else -> false
                    }
                    else -> false
                }
            },
    ) {
        if (state.hasSelection) {
            GridSelectionTopBar(
                selectedCount = state.selection.size,
                customCategories = customCategories,
                onFileIntoFavourites = onFileSelectionIntoFavourites,
                onFileIntoCustom = onFileSelectionIntoCustom,
                onCopySelection = onCopySelection,
                onDeleteSelection = { confirmingDelete = true },
                onClearSelection = onClearSelection,
            )
        } else {
            GridTopBar(
                scope = state.scope,
                currentCategory = currentCategory,
                photoCount = state.photos.size,
                categoryEntries = categoryEntries,
                isBusy = state.isBusy,
                onBack = onBack,
                onSelectCategory = { id -> onSelectCategory(firstVisibleFlat(), id) },
                onCreateCategory = onCreateCategory,
                onRenameCategory = onRenameCategory,
                onDeleteCategory = onDeleteCategory,
                onExportTxt = onExportTxt,
                onCopyToFolder = onCopyToFolder,
                onChangeFolder = onChangeFolder,
                groupingMode = state.groupingMode,
                onSelectGroupingMode = onSelectGroupingModeAnchored,
            )
        }

        if (state.isBusy) {
            BusyBar(label = state.progressLabel ?: "Working…")
        }

        // Non-blocking determinate progress while a grouping lens computes (the cold Similarity pass
        // is a ~minute-long wait). Unlike the busy bar above it doesn't lock the toolbar — the user
        // can keep scrolling the singles grid while the model works.
        state.grouping?.takeIf { it.total > 0 }?.let { g ->
            BusyBar(
                label = "Grouping ${g.processed} / ${g.total}",
                progress = g.processed.toFloat() / g.total,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.photos.isEmpty()) {
                GridEmptyState(
                    scope = state.scope,
                    currentCategory = currentCategory,
                    customCategories = customCategories,
                    onChangeFolder = onChangeFolder,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(AppTheme.dimens.thumbnailMinCell),
                    // Tight contact-sheet gutters. `end` stays wider than the others to leave a
                    // lane for the overlaid scrollbar (xs pad + thickness ~= 12dp) without the
                    // last column running under it.
                    contentPadding = PaddingValues(
                        start = AppTheme.spacing.sm,
                        end = AppTheme.spacing.lg,
                        top = AppTheme.spacing.sm,
                        bottom = AppTheme.spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
                ) {
                    items(
                        items = renderItems,
                        // The burst header spans the full row so the frames beneath it read as one
                        // section; tiles take a single cell.
                        span = { item ->
                            when (item) {
                                is GridRenderItem.BurstHeader -> GridItemSpan(maxLineSpan)
                                is GridRenderItem.BurstFooter -> GridItemSpan(maxLineSpan)
                                is GridRenderItem.Tile -> GridItemSpan(1)
                            }
                        },
                        key = { item ->
                            when (item) {
                                is GridRenderItem.BurstHeader -> "burst-header:" + item.burst.groupId.value
                                is GridRenderItem.BurstFooter -> "burst-footer:" + item.burst.groupId.value
                                is GridRenderItem.Tile -> item.group.groupId.value
                            }
                        },
                    ) { item ->
                        when (item) {
                            is GridRenderItem.BurstHeader -> BurstExpandedHeader(
                                frameCount = item.burst.photos.size,
                                onCollapse = onCollapseBurst,
                            )
                            is GridRenderItem.BurstFooter -> BurstExpandedFooter()
                            is GridRenderItem.Tile -> {
                                val group = item.group
                                val index = item.displayIndex
                                val keyPhoto = group.keyPhoto
                                PhotoThumbnail(
                                    photo = keyPhoto,
                                    loader = imageLoader,
                                    isMarked = keyPhoto.id in state.markedIds,
                                    isFocused = index == state.focusedIndex,
                                    // Any frame of the run counts: a collapsed burst shows the
                                    // middle frame as its key, but you may have opened (and last
                                    // viewed) a different frame, so match against the whole run.
                                    isLastViewed = group.photos.any { it.id == state.lastViewedPhotoId },
                                    // A collapsed burst reads as selected only when its whole run is
                                    // selected, matching the whole-burst pick in toggleSelection.
                                    isSelected = group.photos.all { it.id in state.selection },
                                    onClick = { openTile(index) },
                                    onToggleSelect = { onToggleSelection(index) },
                                    onRangeSelect = { onSelectRange(index) },
                                    categoryBadges = categoryBadgesFor(keyPhoto, customCategories, state.memberships),
                                    burstCount = (group as? PhotoGroup.Burst)?.photos?.size,
                                    withinBurst = item.expandedFrame,
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    interactionSource = scrollbarInteraction,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = AppTheme.spacing.xs),
                    style = ScrollbarStyle(
                        minimalHeight = AppTheme.dimens.scrollbarMinHeight,
                        thickness = AppTheme.dimens.scrollbarThickness,
                        shape = MaterialTheme.shapes.small,
                        hoverDurationMillis = 300,
                        unhoverColor = AppTheme.colors.scrollbarIdle,
                        hoverColor = AppTheme.colors.scrollbarHover,
                    ),
                )
            }

            // Result/notice for bulk and library-level actions (export, copy, bulk file, the survey
            // cap notice) — rendered in the app's pill chrome, not a stock Material snackbar, so all
            // of the grid's transient feedback reads as one family.
            GridMessagePill(
                message = state.toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppTheme.spacing.lg),
            )

            // Transient confirmation that the last F / 1..9 toggle landed and what it did. The
            // tile's star/badge shows the resulting state; this names the action, the way the
            // browser's pill does.
            GridTogglePill(
                toast = categoryToast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppTheme.spacing.lg),
            )
        }

        if (state.photos.isNotEmpty()) {
            GridKeyboardLegend(
                hints = rememberLegendHints(state.scope, currentCategory, onBack != null),
                // Cull progress only makes sense over the whole library, not inside a finished bucket.
                status = if (state.scope == CategoryScope.AllPhotos) {
                    "${state.markedIds.size} favourited"
                } else {
                    null
                },
            )
        }
    }

    // When a selection clears, two cleanups: drop the delete-confirm latch so it can't hang over
    // nothing, and reclaim keyboard focus for the grid. A Cmd-click moves Compose's *actual* focus
    // onto the clicked tile (distinct from our keyboard cursor); deleting that tile removes the
    // focused node, and Compose does NOT fall focus back to the grid - it orphans, so arrows and
    // Enter stop reaching onPreviewKeyEvent until something re-grabs focus. Clearing a selection is
    // the discrete moment after which the grid should own focus again, so re-request it here. Done
    // in an effect (not composition) to avoid a back-write.
    LaunchedEffect(state.hasSelection) {
        if (!state.hasSelection) {
            confirmingDelete = false
            focusRequester.requestFocus()
        }
    }

    // Destructive speed bump in front of the move-to-Trash.
    if (confirmingDelete && state.hasSelection) {
        val count = state.selection.size
        ConfirmDialog(
            title = if (count == 1) "Move 1 photo to Trash?" else "Move $count photos to Trash?",
            message = "The selected " +
                (if (count == 1) "photo" else "photos") +
                " will be moved to the macOS Trash. You can restore " +
                (if (count == 1) "it" else "them") +
                " from there.",
            confirmLabel = "Move to Trash",
            confirmDestructive = true,
            onConfirm = {
                confirmingDelete = false
                onDeleteSelection()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * The truthful set of grid shortcuts for the current [scope]. `F` toggles membership in the
 * scope's active category (Favourites in All Photos, the viewed category otherwise), and the
 * `1..9` filing keys only do anything from All Photos, so they're only advertised there.
 */
@Composable
private fun rememberLegendHints(
    scope: CategoryScope,
    currentCategory: Category?,
    canGoBack: Boolean,
): ImmutableList<KeyHint> = buildList {
    add(KeyHint("← → ↑ ↓", "Move"))
    add(KeyHint("↵", "Open"))
    add(
        KeyHint(
            keys = "F",
            label = if (scope == CategoryScope.AllPhotos) "Favourite" else "Toggle ${currentCategory?.name ?: "category"}",
        ),
    )
    if (scope == CategoryScope.AllPhotos) add(KeyHint("1–9", "Categories"))
    if (canGoBack) add(KeyHint("Esc", "Back"))
}.toImmutableList()

/**
 * The empty-grid guidance, varying by [scope]. All Photos points at the only useful next step
 * (change folder); a category teaches the exact key that files into it — `F` for Favourites,
 * its `1..9` digit for a custom category — so the empty state reinforces the keyboard model
 * rather than dead-ending.
 */
@Composable
private fun GridEmptyState(
    scope: CategoryScope,
    currentCategory: Category?,
    customCategories: List<Category>,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (scope) {
        CategoryScope.AllPhotos -> EmptyState(
            title = "No photos here",
            description = "This folder has no JPEG or PNG images.",
            icon = Icons.Outlined.PhotoLibrary,
            action = {
                AppOutlinedButton(
                    text = "Change folder",
                    leadingIcon = Icons.Default.Folder,
                    onClick = onChangeFolder,
                )
            },
            modifier = modifier,
        )
        is CategoryScope.Category -> {
            val isFavourites = currentCategory?.id == Category.FAVOURITES_ID
            val slot = customCategories.indexOfFirst { it.id == currentCategory?.id }
            val keyHint = when {
                isFavourites -> "F"
                slot in 0..8 -> "${slot + 1}"
                else -> null
            }
            EmptyState(
                title = if (isFavourites) {
                    "No favourites yet"
                } else {
                    "Nothing in ${currentCategory?.name ?: "this category"} yet"
                },
                description = if (keyHint != null) {
                    "In All Photos, focus a photo and press $keyHint to add it here."
                } else {
                    "In All Photos, focus a photo to add it here."
                },
                icon = if (isFavourites) Icons.Outlined.StarOutline else Icons.Outlined.Collections,
                modifier = modifier,
            )
        }
    }
}

/**
 * The fading confirmation pill for a keyboard membership toggle. Lives in its own composable
 * (not under the screen's Column/Row receiver) so the plain `AnimatedVisibility` overload
 * resolves, and holds the last non-null [toast] so its text survives the exit fade. Colour
 * encodes added vs removed; favourites also keep their star — matching the browser's pill.
 */
/** How long a [GridMessagePill] result/notice stays up before it fades out. */
private const val TOAST_DURATION_MS = 2500L

/**
 * Result/notice pill for bulk and library-level actions (export saved, copy report, the survey
 * cap notice). Uses the shared [PillToast] chrome and the same latch + fade as [GridTogglePill], so
 * the grid's transient feedback is one consistent family rather than a stock Material snackbar.
 * The caller drives [message] from `state.toast` and clears it on a timer.
 */
@Composable
private fun GridMessagePill(message: String?, modifier: Modifier = Modifier) {
    // Latch the last message so it stays rendered through the fade-out after the state clears.
    var displayed by remember { mutableStateOf<String?>(null) }
    if (message != null) displayed = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        displayed?.let { PillToast(text = it) }
    }
}

@Composable
private fun GridTogglePill(toast: CategoryToggle?, modifier: Modifier = Modifier) {
    var displayed by remember { mutableStateOf<CategoryToggle?>(null) }
    if (toast != null) displayed = toast
    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        displayed?.let { dt ->
            PillToast(
                text = when {
                    dt.isFavourite && dt.added -> "Favourited"
                    dt.isFavourite -> "Unfavourited"
                    dt.added -> "Added to ${dt.categoryName}"
                    else -> "Removed from ${dt.categoryName}"
                },
                leadingIcon = if (dt.isFavourite) {
                    { FavouriteStar(filled = dt.added, modifier = Modifier.size(AppTheme.dimens.iconSm)) }
                } else {
                    null
                },
                colors = if (dt.added) {
                    PillToastDefaults.addedColors()
                } else {
                    PillToastDefaults.removedColors()
                },
            )
        }
    }
}

/**
 * The digit slots (1..9) of the custom categories [photo] belongs to, for its tile badges.
 * Slot `i+1` matches the key that toggles the i-th custom category. Returns an immutable list
 * so an unchanged result compares equal and the tile stays skippable across membership churn.
 */
private fun categoryBadgesFor(
    photo: Photo,
    customCategories: List<Category>,
    memberships: Map<CategoryId, Set<PhotoId>>,
): ImmutableList<Int> {
    if (customCategories.isEmpty()) return persistentListOf()
    return customCategories.mapIndexedNotNull { i, cat ->
        if (photo.id in memberships[cat.id].orEmpty()) i + 1 else null
    }.toImmutableList()
}

// Maps a flat photo index (the navigation / persistence currency) onto the tile that
// contains it. Tiles are contiguous runs of the flat list, so [tileFlatStart] - each
// tile's first flat index, ascending - is binary-searchable: an exact hit is that tile,
// otherwise the insertion point minus one is the tile whose run straddles the index.
// The grid is the sole translator between flat photo space and its own tile space.
internal fun tileIndexForFlat(tileFlatStart: List<Int>, flatIndex: Int): Int {
    if (tileFlatStart.isEmpty()) return 0
    val i = tileFlatStart.binarySearch(flatIndex)
    return if (i >= 0) i else (-i - 2).coerceIn(0, tileFlatStart.lastIndex)
}

/**
 * Vertical (up/down) keyboard move for the grid cursor, resolved against the *actual* laid-out tile
 * positions rather than `focusedIndex ± columns`. An expanded burst inserts full-width header/footer
 * rows and the partial rows they create, which the arithmetic model can't represent (it made down
 * behave like right). So from the focused tile we pick the nearest tile - by horizontal offset - in
 * the closest tile row in the chosen direction, skipping the non-focusable header/footer items.
 *
 * Falls back to a column-count step only when the focused tile isn't currently laid out (e.g. it was
 * scrolled off after a non-keyboard scroll): the step nudges [focusedIndex] so the focus effect can
 * scroll it back into view, after which geometry resumes. [renderItems] is the LazyGrid's item list
 * (headers/footers included), so an item's `index` there maps to a tile's `displayIndex`.
 */
private fun verticalNavTarget(
    gridState: LazyGridState,
    renderItems: List<GridRenderItem>,
    focusedIndex: Int,
    maxIndex: Int,
    down: Boolean,
): Int {
    // The laid-out tile cells (headers/footers excluded), mapped to the tile index space.
    val tilePositions = gridState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
        (renderItems.getOrNull(info.index) as? GridRenderItem.Tile)
            ?.let { TilePosition(it.displayIndex, info.offset.x, info.offset.y) }
    }
    pickVerticalTarget(tilePositions, focusedIndex, down)?.let { return it }
    // No on-screen tile beyond the cursor (a true edge, or the next row is just off-screen): step by
    // a row so the focus effect can scroll a fresh target into view. Clamps to a no-op at the very
    // top/bottom.
    val columns = computeColumnCount(gridState)
    return if (down) (focusedIndex + columns).coerceAtMost(maxIndex)
    else (focusedIndex - columns).coerceAtLeast(0)
}

// The grid's column count, read from the *widest* laid-out row so a full-width burst header/footer
// row (one spanning item) or a partial row never undercounts it. Used only as the fallback step when
// geometry-based [verticalNavTarget] has no on-screen anchor. Matches what GridCells.Adaptive
// actually laid out (recomputing from viewport width drifts once padding/spacing are accounted for).
// Returns 1 before first layout (visibleItemsInfo empty), which is safe: no arrow can have fired yet.
private fun computeColumnCount(gridState: LazyGridState): Int {
    val visible = gridState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return 1
    return visible.groupingBy { it.offset.y }.eachCount().values.maxOrNull()?.coerceAtLeast(1) ?: 1
}
