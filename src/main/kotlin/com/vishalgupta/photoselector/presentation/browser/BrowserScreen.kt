package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.presentation.common.HoverOverlay
import com.vishalgupta.photoselector.presentation.common.SystemActions
import com.vishalgupta.photoselector.presentation.common.customCategories
import com.vishalgupta.photoselector.presentation.common.digitSlot
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BrowserKeyboardLegend
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ConfirmDialog
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToast
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToastDefaults
import com.vishalgupta.photoselector.presentation.designsystem.organism.BrowserCategoryHud
import com.vishalgupta.photoselector.presentation.designsystem.organism.BrowserTopBar
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** State for the transient confirmation pill shown after a membership toggle. */
data class CategoryToastState(val categoryName: String, val isFavourite: Boolean, val added: Boolean)

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    systemActions: SystemActions,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
    onBack: () -> Unit,
    onCompare: () -> Unit,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
    val state by viewModel.state.collectAsState()
    var toast by remember { mutableStateOf<CategoryToastState?>(null) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    LaunchedEffect(viewModel) {
        viewModel.toggleEvents.collectLatest { event ->
            toast = CategoryToastState(event.categoryName, event.isFavourite, event.added)
            delay(1200)
            toast = null
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.deleteEvents.collectLatest { message ->
            deleteMessage = message
            delay(1600)
            deleteMessage = null
        }
    }

    LaunchedEffect(state.currentPhoto?.id) {
        toast = null
    }

    BrowserScreen(
        state = state,
        toast = toast,
        deleteMessage = deleteMessage,
        systemActions = systemActions,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onToggleCategory = viewModel::toggleCategory,
        onDeleteCurrent = viewModel::deleteCurrent,
        onViewportSizeChanged = viewModel::setViewportLongEdgePx,
        onOpenFavourites = onOpenFavourites,
        onChangeFolder = onChangeFolder,
        onBackToGrid = onBack,
        onCompare = onCompare,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    toast: CategoryToastState?,
    systemActions: SystemActions? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleCategory: (CategoryId) -> Unit,
    onViewportSizeChanged: (Int) -> Unit,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
    onBackToGrid: () -> Unit,
    onCompare: () -> Unit = {},
    deleteMessage: String? = null,
    onDeleteCurrent: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val zoom = rememberZoomState()
    // Open/closed state of the move-to-Trash confirmation; Cmd+Delete arms it.
    var confirmingDelete by remember { mutableStateOf(false) }

    // The HUD auto-hides; any handled keystroke (and, via HoverOverlay, mouse movement)
    // reveals it. Bumping this token restarts the hide timer.
    var revealHud by remember { mutableIntStateOf(0) }
    var keyRevealActive by remember { mutableStateOf(false) }
    LaunchedEffect(revealHud) {
        if (revealHud == 0) return@LaunchedEffect
        keyRevealActive = true
        delay(2500)
        keyRevealActive = false
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(state.currentPhoto?.id) {
        zoom.reset()
    }

    var viewportPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewportPx) {
        if (viewportPx > 0) onViewportSizeChanged(viewportPx)
    }

    // Hold last non-null toast so AnimatedVisibility content renders during exit fade.
    var displayedToast by remember { mutableStateOf<CategoryToastState?>(null) }
    if (toast != null) displayedToast = toast

    // Same latch for the delete confirmation/failure message.
    var displayedDeleteMessage by remember { mutableStateOf<String?>(null) }
    if (deleteMessage != null) displayedDeleteMessage = deleteMessage

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val meta = event.isMetaPressed
                // Bare 1..9 toggles the current photo in the Nth custom category.
                val slot = if (meta) null else digitSlot(event.key)
                if (slot != null) {
                    state.categories.customCategories().getOrNull(slot)?.let { onToggleCategory(it.id) }
                    revealHud++
                    return@onPreviewKeyEvent true
                }
                // Cmd+Delete (Cmd+Backspace on a Mac keyboard) arms the move-to-Trash confirmation
                // for the photo on screen — the macOS "move to trash" chord.
                if (meta && (event.key == Key.Backspace || event.key == Key.Delete)) {
                    if (state.currentPhoto != null) confirmingDelete = true
                    revealHud++
                    return@onPreviewKeyEvent true
                }
                val handled = when (event.key) {
                    Key.DirectionLeft -> { onPrevious(); true }
                    Key.DirectionRight -> { onNext(); true }
                    Key.F -> if (meta) false else { onToggleCategory(Category.FAVOURITES_ID); true }
                    Key.Spacebar -> if (meta) false else {
                        state.currentPhoto?.absolutePath?.let { systemActions?.preview(it) }
                        true
                    }
                    Key.R -> if (meta) false else {
                        state.currentPhoto?.absolutePath?.let { systemActions?.revealInFileManager(it) }
                        true
                    }
                    Key.O -> if (meta) false else {
                        state.currentPhoto?.absolutePath?.let { systemActions?.openWithDefaultApp(it) }
                        true
                    }
                    Key.G -> if (meta) false else { onBackToGrid(); true }
                    Key.C -> if (meta) false else { onCompare(); true }
                    Key.Escape -> { onBackToGrid(); true }
                    Key.Equals, Key.Plus -> { zoom.zoomIn(); true }
                    Key.Minus -> { zoom.zoomOut(); true }
                    Key.Zero -> { zoom.reset(); true }
                    else -> false
                }
                if (handled) revealHud++
                handled
            },
    ) {
        BrowserTopBar(
            countLabel = if (state.photos.isEmpty()) "0 / 0"
            else "${state.currentIndex + 1} / ${state.photos.size}",
            relativePath = state.currentPhoto?.relativePath.orEmpty(),
            favouriteCount = state.favouriteCount,
            readOnly = state.readOnly,
            onBack = onBackToGrid,
            onOpenFavourites = onOpenFavourites,
            onChangeFolder = onChangeFolder,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.photos.isEmpty()) {
            ErrorPlaceholder("No JPEG / PNG photos found in this folder.", Modifier.fillMaxSize())
            return@Box
        }

        HoverOverlay(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = AppTheme.dimens.topBarHeight)
                .onSizeChanged { size ->
                    val px = maxOf(size.width, size.height)
                    if (px > 0) viewportPx = px
                },
        ) { controlsVisible ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bmp = state.currentBitmap
                when {
                    state.isLoadingBitmap && bmp == null -> LoadingIndicator()
                    bmp == null -> ErrorPlaceholder("Cannot decode this photo. Press → to continue.")
                    else -> ZoomableImage(
                        bitmap = bmp,
                        contentDescription = state.currentPhoto?.fileName,
                        zoom = zoom,
                    )
                }

                val alpha by animateFloatAsState(if (controlsVisible) 1f else 0f)
                // The HUD also reveals on keystrokes, so it tracks its own visibility.
                val hudVisible = controlsVisible || keyRevealActive

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = AppTheme.spacing.md)
                        .graphicsLayer { this.alpha = alpha },
                ) {
                    FilledTonalIconButton(onClick = onPrevious) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = AppTheme.spacing.md)
                        .graphicsLayer { this.alpha = alpha },
                ) {
                    FilledTonalIconButton(onClick = onNext) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                    }
                }

                AnimatedVisibility(
                    visible = hudVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AppTheme.spacing.xl),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                    ) {
                        BrowserCategoryHud(
                            categories = state.categories,
                            currentMemberships = state.currentMemberships,
                            onToggle = onToggleCategory,
                        )
                        // Always-on discoverability, folded into the HUD's reveal/auto-hide so the
                        // photo stays unobstructed when idle. Truthful to the key handler above.
                        BrowserKeyboardLegend(
                            hasCustomCategories = state.categories.customCategories().isNotEmpty(),
                            readOnly = state.readOnly,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppTheme.dimens.browserToastBottomInset),
        ) {
            val dt = displayedToast
            if (dt != null) {
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
                    // Colour encodes the action (added vs removed), not which category — a fast
                    // peripheral cue when flipping through a cull. Favourites keeps its star too.
                    colors = if (dt.added) {
                        PillToastDefaults.addedColors()
                    } else {
                        PillToastDefaults.removedColors()
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = deleteMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppTheme.dimens.browserToastBottomInset),
        ) {
            displayedDeleteMessage?.let { PillToast(text = it, colors = PillToastDefaults.removedColors()) }
        }

        if (confirmingDelete) {
            ConfirmDialog(
                title = "Move this photo to Trash?",
                message = "It will be moved to the macOS Trash. You can restore it from there.",
                confirmLabel = "Move to Trash",
                confirmDestructive = true,
                onConfirm = {
                    confirmingDelete = false
                    onDeleteCurrent()
                },
                onDismiss = { confirmingDelete = false },
            )
        }
    }
}
