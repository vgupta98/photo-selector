package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.common.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import com.vishalgupta.photoselector.presentation.common.PhotoThumbnail
import com.vishalgupta.photoselector.presentation.navigation.BrowseScope
import kotlinx.coroutines.launch
import kotlin.math.floor

private val CELL_MIN_SIZE = 160.dp

@Composable
fun GridScreen(
    viewModel: GridViewModel,
    initialScrollIndex: Int,
    onTileClick: (index: Int) -> Unit,
    onChangeFolder: () -> Unit,
    onOpenFavourites: (currentScrollIndex: Int) -> Unit,
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
        onOpenFavourites = onOpenFavourites,
        onBack = onBack,
        onSetFocusedIndex = viewModel::setFocusedIndex,
        onToggleFavouriteAtFocus = viewModel::toggleFavouriteAtFocus,
        onExportTxt = {
            coroutineScope.launch {
                val target = NativeFileDialogs.pickSaveFile(
                    title = "Export favourites list",
                    defaultName = "favourites.txt",
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
    onOpenFavourites: (currentScrollIndex: Int) -> Unit,
    onBack: (() -> Unit)?,
    onSetFocusedIndex: (Int) -> Unit,
    onToggleFavouriteAtFocus: () -> Unit,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    onDismissToast: () -> Unit,
    onFirstVisibleItemChanged: (Int) -> Unit = {},
    imageLoader: com.vishalgupta.photoselector.data.image.ImageLoader,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialScrollIndex)
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    var policyMenu by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val cellMinPx = with(density) { CELL_MIN_SIZE.toPx() }

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
                val cols = computeColumnCount(gridState, cellMinPx)
                val maxIndex = state.photos.size - 1
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
                        onToggleFavouriteAtFocus()
                        true
                    }
                    Key.Spacebar -> if (meta) false else {
                        onToggleFavouriteAtFocus()
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
            state = state,
            onOpenFavourites = { onOpenFavourites(gridState.firstVisibleItemIndex) },
            onBack = onBack,
            onChangeFolder = onChangeFolder,
            onExportTxt = onExportTxt,
            policyMenu = policyMenu,
            onPolicyMenuChange = { policyMenu = it },
            onCopyToFolder = onCopyToFolder,
        )

        if (state.isBusy) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(Modifier.weight(1f))
                Text(state.progressLabel ?: "Working...")
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (state.photos.isEmpty()) {
                val msg = when (state.scope) {
                    BrowseScope.AllPhotos -> "No JPEG / PNG photos found in this folder."
                    BrowseScope.FavouritesOnly -> "No favourites yet. Press back and tap F on a photo to add some."
                }
                ErrorPlaceholder(msg, Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(CELL_MIN_SIZE),
                    contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = state.photos,
                        key = { _, photo -> photo.id.value },
                    ) { index, photo ->
                        PhotoThumbnail(
                            photo = photo,
                            loader = imageLoader,
                            isFavourite = photo.id in state.favouriteIds,
                            isFocused = index == state.focusedIndex,
                            isLastViewed = photo.id == state.lastViewedPhotoId,
                            onClick = { onTileClick(index) },
                        )
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
                    style = ScrollbarStyle(
                        minimalHeight = 48.dp,
                        thickness = 8.dp,
                        shape = RoundedCornerShape(4.dp),
                        hoverDurationMillis = 300,
                        unhoverColor = Color.White.copy(alpha = 0.3f),
                        hoverColor = Color.White.copy(alpha = 0.6f),
                    ),
                )
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) {
                Snackbar(snackbarData = it)
            }
        }
    }
}

@Composable
private fun GridTopBar(
    state: GridUiState,
    onOpenFavourites: () -> Unit,
    onBack: (() -> Unit)?,
    onChangeFolder: () -> Unit,
    onExportTxt: () -> Unit,
    policyMenu: Boolean,
    onPolicyMenuChange: (Boolean) -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        val title = when (state.scope) {
            BrowseScope.AllPhotos -> "${state.photos.size} photos"
            BrowseScope.FavouritesOnly -> "Favourites (${state.photos.size})"
        }
        Text(title, style = MaterialTheme.typography.titleLarge)

        if (state.scope == BrowseScope.AllPhotos) {
            OutlinedButton(onClick = onOpenFavourites) {
                Icon(Icons.Filled.Star, contentDescription = null)
                Text("  Favourites (${state.favouriteIds.size})")
            }
        }

        Box(Modifier.weight(1f))

        if (state.scope == BrowseScope.FavouritesOnly) {
            OutlinedButton(
                enabled = state.favouriteIds.isNotEmpty() && !state.isBusy,
                onClick = onExportTxt,
            ) { Text("Export list (.txt)") }

            Box {
                Button(
                    enabled = state.favouriteIds.isNotEmpty() && !state.isBusy,
                    onClick = { onPolicyMenuChange(true) },
                ) { Text("Copy photos to folder...") }
                DropdownMenu(expanded = policyMenu, onDismissRequest = { onPolicyMenuChange(false) }) {
                    listOf(
                        "If exists: Rename" to ConflictPolicy.RENAME,
                        "If exists: Skip" to ConflictPolicy.SKIP,
                        "If exists: Overwrite" to ConflictPolicy.OVERWRITE,
                    ).forEach { (label, policy) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onPolicyMenuChange(false)
                                onCopyToFolder(policy)
                            },
                        )
                    }
                }
            }
        }

        TextButton(onClick = onChangeFolder) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Text("  Change folder")
        }
    }
}

private fun computeColumnCount(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    cellMinPx: Float,
): Int {
    val info = gridState.layoutInfo
    val viewportWidth = info.viewportSize.width.toFloat()
    if (viewportWidth <= 0f) return 1
    return floor(viewportWidth / cellMinPx).toInt().coerceAtLeast(1)
}
