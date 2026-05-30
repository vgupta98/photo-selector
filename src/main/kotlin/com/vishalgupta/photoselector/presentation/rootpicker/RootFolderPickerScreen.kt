package com.vishalgupta.photoselector.presentation.rootpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun RootFolderPickerScreen(viewModel: RootFolderPickerViewModel) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    RootFolderPickerScreen(
        state = state,
        onPickFolder = {
            coroutineScope.launch {
                val picked = NativeFileDialogs.pickDirectory("Choose photo folder")
                if (picked != null) viewModel.startScan(picked)
            }
        },
        onCancelScan = viewModel::cancelScan,
    )
}

@Composable
fun RootFolderPickerScreen(
    state: RootPickerUiState,
    onPickFolder: () -> Unit,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().padding(PaddingValues(AppTheme.spacing.xxl)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg),
        ) {
            Text("Photo Selector", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Pick a root folder. Subfolders are scanned recursively.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state.phase) {
                RootPickerUiState.Phase.Idle, RootPickerUiState.Phase.Done -> {
                    AppButton(text = "Choose photo folder…", onClick = onPickFolder)
                }
                RootPickerUiState.Phase.Scanning -> {
                    LoadingIndicator(Modifier.size(AppTheme.dimens.progressIndicatorLg))
                    Text("Scanning…  ${state.found} photos found  (${state.scanned} files seen)")
                    AppOutlinedButton(text = "Cancel", onClick = onCancelScan)
                }
                RootPickerUiState.Phase.Failed -> {
                    Text(
                        "Scan failed: ${state.errorMessage ?: "Unknown error"}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    AppButton(text = "Try again", onClick = onPickFolder)
                }
            }
        }
    }
}
