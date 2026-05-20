package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.common.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.common.HoverOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenFavourites: (currentIndex: Int) -> Unit,
    onChangeFolder: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }

    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val zoom = rememberZoomState()
    var toastFavourite by remember { mutableStateOf<Boolean?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    var allDecidedVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.loadIfNeeded()
    }

    LaunchedEffect(viewModel) {
        viewModel.toggleEvents.collectLatest { nowFavourite ->
            toastFavourite = nowFavourite
            toastVisible = true
            delay(1200)
            toastVisible = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collectLatest { event ->
            when (event) {
                NavigationEvent.AllDecided -> {
                    allDecidedVisible = true
                    delay(1500)
                    allDecidedVisible = false
                }
            }
        }
    }

    LaunchedEffect(state.currentPhoto?.id) {
        toastVisible = false
        zoom.reset()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft ->
                        { if (event.isShiftPressed) viewModel.previousStep() else viewModel.previous(); true }
                    Key.DirectionRight ->
                        { if (event.isShiftPressed) viewModel.nextStep() else viewModel.next(); true }
                    Key.U -> { viewModel.toggleNavigationMode(); true }
                    Key.F, Key.Spacebar -> { viewModel.toggleFavourite(); true }
                    Key.Equals, Key.Plus -> { zoom.zoomIn(); true }
                    Key.Minus -> { zoom.zoomOut(); true }
                    Key.Zero -> { zoom.reset(); true }
                    else -> false
                }
            },
    ) {
        TopBar(
            countLabel = if (state.photos.isEmpty()) "0 / 0"
                else "${state.currentIndex + 1} / ${state.photos.size}",
            relativePath = state.currentPhoto?.relativePath.orEmpty(),
            favCount = state.favouriteCount,
            readOnly = state.readOnly,
            navigationMode = state.navigationMode,
            onToggleNavigationMode = viewModel::toggleNavigationMode,
            onBack = onBack,
            onOpenFavourites = { onOpenFavourites(state.currentIndex) },
            onChangeFolder = onChangeFolder,
        )

        if (state.photos.isEmpty()) {
            ErrorPlaceholder("No JPEG / PNG photos found in this folder.")
            return@Box
        }

        HoverOverlay(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp)
                .onSizeChanged { size ->
                    val px = maxOf(size.width, size.height)
                    if (px > 0) viewModel.setViewportLongEdgePx(px)
                },
        ) { controlsVisible ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bmp = state.currentBitmap
                when {
                    state.isLoadingBitmap && bmp == null -> CircularProgressIndicator()
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
                        .padding(start = 12.dp)
                        .alpha(alpha),
                ) {
                    FilledTonalIconButton(onClick = viewModel::previous) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .alpha(alpha),
                ) {
                    FilledTonalIconButton(onClick = viewModel::next) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .alpha(alpha),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalIconButton(onClick = viewModel::toggleFavourite) {
                        if (state.isCurrentFavourite) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Unfavourite",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Outlined.StarOutline, contentDescription = "Favourite")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toastVisible && toastFavourite != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) {
            val isFav = toastFavourite == true
            FavouriteToast(
                isFavourite = isFav,
                label = if (isFav) "Favourited" else "Unfavourited",
            )
        }

        AnimatedVisibility(
            visible = allDecidedVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 144.dp),
        ) {
            AllDecidedToast()
        }
    }
}

@Composable
private fun AllDecidedToast() {
    Surface(
        color = Color(0xFF2A2A2A),
        contentColor = Color(0xFFE6E6E6),
        shape = RoundedCornerShape(percent = 50),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = "All photos decided",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun FavouriteToast(isFavourite: Boolean, label: String) {
    val bg = if (isFavourite) Color(0xFFE9A93C) else Color(0xFF2A2A2A)
    val fg = if (isFavourite) Color(0xFF1A1A1A) else Color(0xFFE6E6E6)
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(percent = 50),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TopBar(
    countLabel: String,
    relativePath: String,
    favCount: Int,
    readOnly: Boolean,
    navigationMode: NavigationMode,
    onToggleNavigationMode: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Text(countLabel, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "—  $relativePath",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (readOnly) {
            Text(
                "Read-only folder · selections in-memory only",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        NavigationModeChip(
            mode = navigationMode,
            onClick = onToggleNavigationMode,
        )
        TextButton(onClick = onOpenFavourites) {
            Icon(Icons.Outlined.Star, contentDescription = null)
            Text("  Favourites ($favCount)")
        }
        TextButton(onClick = onChangeFolder) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Text("  Change folder")
        }
    }
}

@Composable
private fun NavigationModeChip(mode: NavigationMode, onClick: () -> Unit) {
    val isSkip = mode == NavigationMode.SKIP_DECIDED
    val label = if (isSkip) "Next undecided" else "Step"
    val bg = if (isSkip) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
    val fg = if (isSkip) MaterialTheme.colorScheme.onPrimary else Color.White
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(percent = 50),
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
