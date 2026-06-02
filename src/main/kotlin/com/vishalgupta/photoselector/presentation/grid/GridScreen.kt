package com.vishalgupta.photoselector.presentation.grid

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import com.vishalgupta.photoselector.presentation.common.digitSlot
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BusyBar
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.designsystem.molecule.GridKeyboardLegend
import com.vishalgupta.photoselector.presentation.designsystem.molecule.KeyHint
import com.vishalgupta.photoselector.presentation.designsystem.organism.GridTopBar
import com.vishalgupta.photoselector.presentation.designsystem.organism.PhotoThumbnail
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import kotlinx.coroutines.launch

@Composable
fun GridScreen(
    viewModel: GridViewModel,
    initialScrollIndex: Int,
    onTileClick: (index: Int) -> Unit,
    onChangeFolder: () -> Unit,
    onSelectCategory: (currentScrollIndex: Int, id: CategoryId) -> Unit,
    onBack: (() -> Unit)?,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    GridScreen(
        state = state,
        initialScrollIndex = initialScrollIndex,
        onTileClick = onTileClick,
        onChangeFolder = onChangeFolder,
        onSelectCategory = onSelectCategory,
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
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialScrollIndex)
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentCategory: Category? = (state.scope as? CategoryScope.Category)
        ?.let { sc -> state.categories.firstOrNull { it.id == sc.id } }
    val categoryEntries = state.categories.map { it to (state.memberships[it.id]?.size ?: 0) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
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
                val cols = computeColumnCount(gridState)
                val maxIndex = state.photos.size - 1
                val isArrow = event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown
                if (isArrow && state.focusedIndex < 0 && maxIndex >= 0) {
                    onSetFocusedIndex(gridState.firstVisibleItemIndex.coerceIn(0, maxIndex))
                    return@onPreviewKeyEvent true
                }
                // Bare 1..9 toggles the focused photo in the Nth custom category — the keyboard
                // path for filing into a category without leaving All Photos.
                val slot = if (meta) null else digitSlot(event.key)
                if (slot != null) {
                    if (state.focusedIndex in 0..maxIndex) onToggleCustomCategoryAtFocus(slot)
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
                        onToggleMembershipAtFocus()
                        true
                    }
                    Key.Spacebar -> if (meta) false else {
                        onToggleMembershipAtFocus()
                        true
                    }
                    Key.Escape -> if (onBack != null) {
                        onBack()
                        true
                    } else false
                    else -> false
                }
            },
    ) {
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

        if (state.isBusy) {
            BusyBar(label = state.progressLabel ?: "Working…")
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.photos.isEmpty()) {
                val msg = when (state.scope) {
                    CategoryScope.AllPhotos -> "No JPEG / PNG photos found in this folder."
                    is CategoryScope.Category ->
                        "No photos in ${currentCategory?.name ?: "this category"} yet. " +
                            "From All Photos, focus a photo and press F (Favourites) or 1..9 to add it."
                }
                ErrorPlaceholder(msg, Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(AppTheme.dimens.thumbnailMinCell),
                    contentPadding = PaddingValues(
                        start = AppTheme.spacing.md,
                        end = AppTheme.spacing.xl,
                        top = AppTheme.spacing.md,
                        bottom = AppTheme.spacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
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
                            onClick = { onTileClick(index) },
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

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) {
                Snackbar(snackbarData = it)
            }
        }

        if (state.photos.isNotEmpty()) {
            GridKeyboardLegend(hints = rememberLegendHints(state.scope, currentCategory, onBack != null))
        }
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
): List<KeyHint> = buildList {
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
