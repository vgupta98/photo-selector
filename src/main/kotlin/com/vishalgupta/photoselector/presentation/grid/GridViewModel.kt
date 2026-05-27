package com.vishalgupta.photoselector.presentation.grid

import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import com.vishalgupta.photoselector.domain.usecase.CopyFavouritesToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportFavouritesTxtUseCase
import com.vishalgupta.photoselector.domain.usecase.ObserveFavouritesUseCase
import com.vishalgupta.photoselector.domain.usecase.ToggleFavouriteUseCase
import com.vishalgupta.photoselector.presentation.StateHolder
import com.vishalgupta.photoselector.presentation.navigation.BrowseScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

data class GridUiState(
    val photos: List<Photo> = emptyList(),
    val scope: BrowseScope = BrowseScope.AllPhotos,
    val favouriteIds: Set<PhotoId> = emptySet(),
    val lastViewedPhotoId: PhotoId? = null,
    val focusedIndex: Int = -1,
    val isBusy: Boolean = false,
    val progressLabel: String? = null,
    val toast: String? = null,
)

class GridViewModel(
    private val root: RootFolder,
    private val allPhotos: List<Photo>,
    initialScope: BrowseScope,
    lastViewedPhotoId: PhotoId? = null,
    private val observeFavourites: ObserveFavouritesUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val exportTxt: ExportFavouritesTxtUseCase,
    private val copyToFolder: CopyFavouritesToFolderUseCase,
    val imageLoader: ImageLoader,
    parentJob: Job? = null,
) : StateHolder(parentJob) {

    private val _scope = MutableStateFlow(initialScope)
    private val _state = MutableStateFlow(GridUiState(scope = initialScope, lastViewedPhotoId = lastViewedPhotoId))
    val state: StateFlow<GridUiState> = _state.asStateFlow()

    init {
        combine(observeFavourites(root), _scope) { favIds, browseScope ->
            favIds to browseScope
        }
            .onEach { (favIds, browseScope) ->
                val photos = when (browseScope) {
                    BrowseScope.AllPhotos -> allPhotos
                    BrowseScope.FavouritesOnly -> {
                        val byId = allPhotos.associateBy { it.id }
                        favIds.mapNotNull { byId[it] }.sortedBy { it.relativePath }
                    }
                }
                _state.update {
                    it.copy(
                        photos = photos,
                        scope = browseScope,
                        favouriteIds = favIds,
                        focusedIndex = it.focusedIndex.coerceIn(-1, (photos.size - 1).coerceAtLeast(-1)),
                    )
                }
            }
            .launchIn(scope)
    }

    fun toggleScope() {
        val newScope = when (_scope.value) {
            BrowseScope.AllPhotos -> BrowseScope.FavouritesOnly
            BrowseScope.FavouritesOnly -> BrowseScope.AllPhotos
        }
        _scope.value = newScope
        _state.update { it.copy(focusedIndex = -1) }
    }

    fun setFocusedIndex(index: Int) {
        _state.update { it.copy(focusedIndex = index.coerceIn(-1, (it.photos.size - 1).coerceAtLeast(-1))) }
    }

    fun toggleFavouriteAtFocus() {
        val photo = _state.value.photos.getOrNull(_state.value.focusedIndex) ?: return
        scope.launch { toggleFavourite(root, photo.id) }
    }

    fun toggleFavourite(photoId: PhotoId) {
        scope.launch { toggleFavourite(root, photoId) }
    }

    fun exportTxt(destination: Path) {
        scope.launch {
            _state.update { it.copy(isBusy = true, progressLabel = "Writing list...") }
            try {
                val favourites = currentFavouritePhotos()
                exportTxt.invoke(root, favourites, destination)
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = null,
                        toast = "Saved ${favourites.size} entries to ${destination.fileName}",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isBusy = false, progressLabel = null, toast = "Export failed: ${t.message}")
                }
            }
        }
    }

    fun copyTo(destination: Path, policy: ConflictPolicy) {
        scope.launch {
            val favourites = currentFavouritePhotos()
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
                    it.copy(isBusy = false, progressLabel = null, toast = buildReportToast(report))
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isBusy = false, progressLabel = null, toast = "Copy failed: ${t.message}")
                }
            }
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun currentFavouritePhotos(): List<Photo> {
        val favIds = _state.value.favouriteIds
        return allPhotos.filter { it.id in favIds }.sortedBy { it.relativePath }
    }

    private fun buildReportToast(report: CopyReport): String {
        val parts = mutableListOf("Copied ${report.copied}")
        if (report.skipped > 0) parts += "skipped ${report.skipped}"
        if (report.failed.isNotEmpty()) parts += "${report.failed.size} failed"
        return parts.joinToString(", ")
    }
}
