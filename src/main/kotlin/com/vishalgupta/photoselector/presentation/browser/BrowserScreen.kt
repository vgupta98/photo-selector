package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.common.HoverOverlay
import com.vishalgupta.photoselector.presentation.common.SystemActions
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToast
import com.vishalgupta.photoselector.presentation.designsystem.organism.BrowserTopBar
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

data class FavouriteToastState(val isFavourite: Boolean)

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    systemActions: SystemActions,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
    onBack: () -> Unit,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
    val state by viewModel.state.collectAsState()
    var toast by remember { mutableStateOf<FavouriteToastState?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    LaunchedEffect(viewModel) {
        viewModel.toggleEvents.collectLatest { nowFavourite ->
            toast = FavouriteToastState(nowFavourite)
            delay(1200)
            toast = null
        }
    }

    LaunchedEffect(state.currentPhoto?.id) {
        toast = null
    }

    BrowserScreen(
        state = state,
        toast = toast,
        systemActions = systemActions,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onToggleFavourite = viewModel::toggleFavourite,
        onViewportSizeChanged = viewModel::setViewportLongEdgePx,
        onOpenFavourites = onOpenFavourites,
        onChangeFolder = onChangeFolder,
        onBackToGrid = onBack,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    toast: FavouriteToastState?,
    systemActions: SystemActions? = null,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavourite: () -> Unit,
    onViewportSizeChanged: (Int) -> Unit,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
    onBackToGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val zoom = rememberZoomState()

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
    var displayedToast by remember { mutableStateOf<FavouriteToastState?>(null) }
    if (toast != null) displayedToast = toast

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val meta = event.isMetaPressed
                when (event.key) {
                    Key.DirectionLeft -> { onPrevious(); true }
                    Key.DirectionRight -> { onNext(); true }
                    Key.F -> if (meta) false else { onToggleFavourite(); true }
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
                    Key.Escape -> { onBackToGrid(); true }
                    Key.Equals, Key.Plus -> { zoom.zoomIn(); true }
                    Key.Minus -> { zoom.zoomOut(); true }
                    Key.Zero -> { zoom.reset(); true }
                    else -> false
                }
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

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AppTheme.spacing.xl)
                        .graphicsLayer { this.alpha = alpha },
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
                ) {
                    FilledTonalIconButton(onClick = onToggleFavourite) {
                        FavouriteStar(
                            filled = state.isCurrentFavourite,
                            tint = if (state.isCurrentFavourite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                            contentDescription = if (state.isCurrentFavourite) "Unfavourite" else "Favourite",
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
                .padding(bottom = 96.dp),
        ) {
            val dt = displayedToast
            if (dt != null) {
                PillToast(
                    text = if (dt.isFavourite) "Favourited" else "Unfavourited",
                    leadingIcon = {
                        FavouriteStar(
                            filled = dt.isFavourite,
                            modifier = Modifier.size(AppTheme.dimens.iconSm),
                        )
                    },
                    containerColor = if (dt.isFavourite) {
                        AppTheme.colors.favouriteToastBackground
                    } else {
                        AppTheme.colors.toastBackground
                    },
                    contentColor = if (dt.isFavourite) {
                        AppTheme.colors.favouriteToastContent
                    } else {
                        AppTheme.colors.toastContent
                    },
                )
            }
        }
    }
}
