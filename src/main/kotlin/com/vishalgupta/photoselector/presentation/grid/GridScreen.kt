package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.common.CategoryToggle
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import com.vishalgupta.photoselector.presentation.common.customCategories
import com.vishalgupta.photoselector.presentation.common.digitSlot
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
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
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
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

@Composable
fun GridScreen(
    state: GridUiState,
    initialScrollIndex: Int,
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
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialScrollIndex)
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(TOAST_DURATION_MS)
            onDismissToast()
        }
    }

    LaunchedEffect(state.focusedIndex) {
        val idx = state.focusedIndex
        if (idx < 0) return@LaunchedEffect
        val layout = gridState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == idx }
        val isFullyVisible = item != null &&
            item.offset.y >= layout.viewportStartOffset &&
            item.offset.y + item.size.height <= layout.viewportEndOffset
        if (!isFullyVisible) {
            gridState.animateScrollToItem(idx)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { index -> onFirstVisibleItemChanged(index) }
    }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val meta = event.isMetaPressed
                val hasSelection = state.hasSelection
                val cols = computeColumnCount(gridState)
                val maxIndex = state.photos.size - 1
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
                        onCompareSelection(indices, gridState.firstVisibleItemIndex)
                    } else {
                        onSelectionTooLargeToCompare()
                    }
                    return@onPreviewKeyEvent true
                }
                val isArrow = event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown
                if (isArrow && state.focusedIndex < 0 && maxIndex >= 0) {
                    onSetFocusedIndex(gridState.firstVisibleItemIndex.coerceIn(0, maxIndex))
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
                        onSetFocusedIndex((state.focusedIndex - 1).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight -> {
                        onSetFocusedIndex((state.focusedIndex + 1).coerceAtMost(maxIndex))
                        true
                    }
                    Key.DirectionUp -> {
                        onSetFocusedIndex((state.focusedIndex - cols).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionDown -> {
                        onSetFocusedIndex((state.focusedIndex + cols).coerceAtMost(maxIndex))
                        true
                    }
                    Key.Enter -> {
                        if (state.focusedIndex in 0..maxIndex) {
                            onTileClick(state.focusedIndex)
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
                    // Esc clears an active selection first; only an already-empty grid pops the screen.
                    Key.Escape -> when {
                        hasSelection -> {
                            onClearSelection()
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
                onSelectCategory = { id -> onSelectCategory(gridState.firstVisibleItemIndex, id) },
                onCreateCategory = onCreateCategory,
                onRenameCategory = onRenameCategory,
                onDeleteCategory = onDeleteCategory,
                onExportTxt = onExportTxt,
                onCopyToFolder = onCopyToFolder,
                onChangeFolder = onChangeFolder,
            )
        }

        if (state.isBusy) {
            BusyBar(label = state.progressLabel ?: "Working…")
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
                    itemsIndexed(
                        items = state.photos,
                        key = { _, photo -> photo.id.value },
                    ) { index, photo ->
                        PhotoThumbnail(
                            photo = photo,
                            loader = imageLoader,
                            isMarked = photo.id in state.markedIds,
                            isFocused = index == state.focusedIndex,
                            isLastViewed = photo.id == state.lastViewedPhotoId,
                            isSelected = photo.id in state.selection,
                            onClick = { onTileClick(index) },
                            onToggleSelect = { onToggleSelection(index) },
                            onRangeSelect = { onSelectRange(index) },
                            categoryBadges = categoryBadgesFor(photo, customCategories, state.memberships),
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
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

    // If the selection clears out from under an open dialog, drop the latch rather than leave a
    // confirm hanging over nothing. Done in an effect (not composition) to avoid a back-write.
    LaunchedEffect(state.hasSelection) {
        if (!state.hasSelection) confirmingDelete = false
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

// Derives the column count from the rendered grid (count of leading visible items
// sharing the first row's y-offset) so the keyboard model matches what
// GridCells.Adaptive actually laid out — recomputing from viewport width drifts
// once contentPadding / horizontalArrangement spacing are taken into account.
// Returns 1 before first layout (visibleItemsInfo empty), which is safe: the
// user can't have pressed an arrow yet.
private fun computeColumnCount(gridState: LazyGridState): Int {
    val visible = gridState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return 1
    val firstRowY = visible.first().offset.y
    return visible.count { it.offset.y == firstRowY }.coerceAtLeast(1)
}
