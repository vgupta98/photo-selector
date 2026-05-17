package com.vishalgupta.photoselector.presentation.rootpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.common.NativeFileDialogs
import kotlinx.coroutines.launch

@Composable
fun RootFolderPickerScreen(viewModel: RootFolderPickerViewModel) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }

    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(PaddingValues(32.dp)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Photo Selector", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Pick a root folder. Subfolders are scanned recursively.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state.phase) {
                RootPickerUiState.Phase.Idle, RootPickerUiState.Phase.Done -> {
                    Button(onClick = {
                        coroutineScope.launch {
                            val picked = NativeFileDialogs.pickDirectory("Choose photo folder")
                            if (picked != null) viewModel.startScan(picked)
                        }
                    }) {
                        Text("Choose photo folder…")
                    }
                }
                RootPickerUiState.Phase.Scanning -> {
                    CircularProgressIndicator(Modifier.size(48.dp))
                    Text("Scanning…  ${state.found} photos found  (${state.scanned} files seen)")
                    OutlinedButton(onClick = { viewModel.cancelScan() }) { Text("Cancel") }
                }
                RootPickerUiState.Phase.Failed -> {
                    Text(
                        "Scan failed: ${state.errorMessage ?: "Unknown error"}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = {
                        coroutineScope.launch {
                            val picked = NativeFileDialogs.pickDirectory("Choose photo folder")
                            if (picked != null) viewModel.startScan(picked)
                        }
                    }) { Text("Try again") }
                }
            }
        }
    }
}
