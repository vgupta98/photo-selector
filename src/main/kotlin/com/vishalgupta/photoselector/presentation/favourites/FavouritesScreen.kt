package com.vishalgupta.photoselector.presentation.favourites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.presentation.common.PhotoThumbnail
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.common.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import kotlinx.coroutines.launch

@Composable
fun FavouritesScreen(
    viewModel: FavouritesViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (Photo) -> Unit,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    FavouritesScreen(
        state = state,
        onBack = onBack,
        onOpenPhoto = onOpenPhoto,
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
        imageLoader = viewModel.imageLoader,
    )
}

@Composable
fun FavouritesScreen(
    state: FavouritesUiState,
    onBack: () -> Unit,
    onOpenPhoto: (Photo) -> Unit,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    onDismissToast: () -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var policyMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            onDismissToast()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Favourites (${state.favourites.size})", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.weight(1f))
            OutlinedButton(
                enabled = state.favourites.isNotEmpty() && !state.isBusy,
                onClick = onExportTxt,
            ) { Text("Export list (.txt)") }

            Box {
                Button(
                    enabled = state.favourites.isNotEmpty() && !state.isBusy,
                    onClick = { policyMenu = true },
                ) { Text("Copy photos to folder…") }
                DropdownMenu(expanded = policyMenu, onDismissRequest = { policyMenu = false }) {
                    listOf(
                        "If exists: Rename" to ConflictPolicy.RENAME,
                        "If exists: Skip" to ConflictPolicy.SKIP,
                        "If exists: Overwrite" to ConflictPolicy.OVERWRITE,
                    ).forEach { (label, policy) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                policyMenu = false
                                onCopyToFolder(policy)
                            },
                        )
                    }
                }
            }
        }

        if (state.isBusy) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(Modifier.weight(1f))
                Text(state.progressLabel ?: "Working…")
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (state.favourites.isEmpty()) {
                ErrorPlaceholder(
                    "No favourites yet. Press F (or the star button) in the browser to add some.",
                    Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.favourites, key = { it.id.value }) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            loader = imageLoader,
                            isFavourite = false,
                            isFocused = false,
                            onClick = { onOpenPhoto(photo) },
                        )
                    }
                }
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter)) {
                Snackbar(snackbarData = it)
            }
        }
    }
}
