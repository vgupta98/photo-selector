package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.common.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.common.HoverOverlay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }

    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.loadIfNeeded()
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
                    Key.DirectionLeft -> { viewModel.previous(); true }
                    Key.DirectionRight -> { viewModel.next(); true }
                    Key.F, Key.Spacebar -> { viewModel.toggleFavourite(); true }
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
            onOpenFavourites = onOpenFavourites,
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
                    else -> Image(
                        bitmap = bmp,
                        contentDescription = state.currentPhoto?.fileName,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
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
    }
}

@Composable
private fun TopBar(
    countLabel: String,
    relativePath: String,
    favCount: Int,
    readOnly: Boolean,
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
