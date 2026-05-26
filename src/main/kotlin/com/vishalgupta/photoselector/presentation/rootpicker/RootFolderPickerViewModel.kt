package com.vishalgupta.photoselector.presentation.rootpicker

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.model.ScanProgress
import com.vishalgupta.photoselector.domain.usecase.ScanRootFolderUseCase
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

data class RootPickerUiState(
    val phase: Phase = Phase.Idle,
    val scanned: Int = 0,
    val found: Int = 0,
    val errorMessage: String? = null,
) {
    enum class Phase { Idle, Scanning, Done, Failed }
}

class RootFolderPickerViewModel(
    private val scanRootFolder: ScanRootFolderUseCase,
    private val onScanComplete: (RootFolder, List<Photo>) -> Unit,
    parentJob: Job? = null,
) : StateHolder(parentJob) {

    private val _state = MutableStateFlow(RootPickerUiState())
    val state: StateFlow<RootPickerUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(path: Path) {
        scanJob?.cancel()
        val root = RootFolder(path)
        _state.value = RootPickerUiState(phase = RootPickerUiState.Phase.Scanning)
        scanJob = scope.launch {
            scanRootFolder(root).collect { progress ->
                when (progress) {
                    is ScanProgress.InProgress -> _state.update {
                        it.copy(
                            phase = RootPickerUiState.Phase.Scanning,
                            scanned = progress.scanned,
                            found = progress.foundPhotos,
                        )
                    }
                    is ScanProgress.Done -> {
                        _state.update {
                            it.copy(
                                phase = RootPickerUiState.Phase.Done,
                                found = progress.photos.size,
                            )
                        }
                        onScanComplete(root, progress.photos)
                    }
                    is ScanProgress.Failed -> _state.update {
                        it.copy(
                            phase = RootPickerUiState.Phase.Failed,
                            errorMessage = progress.error.message ?: "Scan failed",
                        )
                    }
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.value = RootPickerUiState()
    }
}
