package com.vishalgupta.photoselector.presentation.favourites

import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import com.vishalgupta.photoselector.domain.usecase.CopyFavouritesToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportFavouritesTxtUseCase
import com.vishalgupta.photoselector.domain.usecase.ObserveFavouritesUseCase
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

data class FavouritesUiState(
    val favourites: List<Photo> = emptyList(),
    val isBusy: Boolean = false,
    val progressLabel: String? = null,
    val toast: String? = null,
)

class FavouritesViewModel(
    private val root: RootFolder,
    private val allPhotos: List<Photo>,
    private val observeFavourites: ObserveFavouritesUseCase,
    private val exportTxt: ExportFavouritesTxtUseCase,
    private val copyToFolder: CopyFavouritesToFolderUseCase,
    val imageLoader: ImageLoader,
    parentJob: Job? = null,
) : StateHolder(parentJob) {

    private val _state = MutableStateFlow(FavouritesUiState())
    val state: StateFlow<FavouritesUiState> = _state.asStateFlow()

    init {
        observeFavourites(root)
            .onEach { ids ->
                val byId = allPhotos.associateBy { it.id }
                val list = ids.mapNotNull { byId[it] }.sortedBy { it.relativePath }
                _state.update { it.copy(favourites = list) }
            }
            .launchIn(scope)
    }

    fun exportTxt(destination: Path) {
        scope.launch {
            _state.update { it.copy(isBusy = true, progressLabel = "Writing list…") }
            try {
                exportTxt.invoke(root, _state.value.favourites, destination)
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = null,
                        toast = "Saved ${it.favourites.size} entries to ${destination.fileName}",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = null,
                        toast = "Export failed: ${t.message}",
                    )
                }
            }
        }
    }

    fun copyTo(destination: Path, policy: ConflictPolicy) {
        scope.launch {
            val favourites = _state.value.favourites
            _state.update { it.copy(isBusy = true, progressLabel = "0 / ${favourites.size}") }
            try {
                val report: CopyReport = copyToFolder.invoke(
                    root = root,
                    favourites = favourites,
                    destDir = destination,
                    policy = policy,
                ) { done, total ->
                    _state.update { it.copy(progressLabel = "$done / $total") }
                }
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = null,
                        toast = buildReportToast(report),
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = null,
                        toast = "Copy failed: ${t.message}",
                    )
                }
            }
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun buildReportToast(report: CopyReport): String {
        val parts = mutableListOf("Copied ${report.copied}")
        if (report.skipped > 0) parts += "skipped ${report.skipped}"
        if (report.failed.isNotEmpty()) parts += "${report.failed.size} failed"
        return parts.joinToString(", ")
    }
}
