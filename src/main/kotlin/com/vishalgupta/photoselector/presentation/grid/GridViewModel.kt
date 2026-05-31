package com.vishalgupta.photoselector.presentation.grid

import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import com.vishalgupta.photoselector.domain.usecase.CopyPhotosToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportPhotosTxtUseCase
import com.vishalgupta.photoselector.presentation.StateHolder
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.navigation.activeCategoryId
import com.vishalgupta.photoselector.presentation.navigation.slice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val scope: CategoryScope = CategoryScope.AllPhotos,
    val categories: List<Category> = emptyList(),
    val memberships: Map<CategoryId, Set<PhotoId>> = emptyMap(),
    val lastViewedPhotoId: PhotoId? = null,
    val focusedIndex: Int = -1,
    val isBusy: Boolean = false,
    val progressLabel: String? = null,
    val toast: String? = null,
) {
    /** Photos marked in the category the focused-tile toggle acts on (Favourites in All Photos). */
    val markedIds: Set<PhotoId> get() = memberships[scope.activeCategoryId].orEmpty()
}

class GridViewModel(
    private val root: RootFolder,
    private val allPhotos: List<Photo>,
    private val categoryScope: CategoryScope,
    lastViewedPhotoId: PhotoId? = null,
    private val categories: CategoriesRepository,
    private val exportTxt: ExportPhotosTxtUseCase,
    private val copyToFolder: CopyPhotosToFolderUseCase,
    val imageLoader: ImageLoader,
    parentJob: Job? = null,
    private val onScrollIndexChanged: ((Int) -> Unit)? = null,
) : StateHolder(parentJob) {

    private val _state = MutableStateFlow(GridUiState(scope = categoryScope, lastViewedPhotoId = lastViewedPhotoId))
    val state: StateFlow<GridUiState> = _state.asStateFlow()

    private var scrollSaveJob: Job? = null
    private var lastKnownIndex: Int? = null

    init {
        combine(
            categories.observeCategories(root),
            categories.observeMemberships(root),
        ) { cats, members -> cats to members }
            .onEach { (cats, members) ->
                val photos = categoryScope.slice(allPhotos, members[categoryScope.activeCategoryId].orEmpty())
                _state.update {
                    it.copy(
                        photos = photos,
                        categories = cats,
                        memberships = members,
                        focusedIndex = it.focusedIndex.coerceIn(-1, (photos.size - 1).coerceAtLeast(-1)),
                    )
                }
            }
            .launchIn(scope)
    }

    fun onFirstVisibleItemChanged(index: Int) {
        if (categoryScope != CategoryScope.AllPhotos) return
        lastKnownIndex = index
        onScrollIndexChanged?.let { save ->
            scrollSaveJob?.cancel()
            scrollSaveJob = scope.launch {
                delay(500)
                save(index)
                lastKnownIndex = null
            }
        }
    }

    override fun onClear() {
        val pending = lastKnownIndex
        if (pending != null) {
            onScrollIndexChanged?.invoke(pending)
        }
        super.onClear()
    }

    fun setFocusedIndex(index: Int) {
        _state.update { it.copy(focusedIndex = index.coerceIn(-1, (it.photos.size - 1).coerceAtLeast(-1))) }
    }

    /** Toggles the focused photo in the active category (the scoped one, or Favourites in All Photos). */
    fun toggleMembershipAtFocus() {
        val photo = _state.value.photos.getOrNull(_state.value.focusedIndex) ?: return
        scope.launch { categories.toggleMembership(root, categoryScope.activeCategoryId, photo.id) }
    }

    /** Toggles the focused photo in the category at [displayIndex] (Cmd+1..9), if one exists. */
    fun toggleCategoryAtFocus(displayIndex: Int) {
        val photo = _state.value.photos.getOrNull(_state.value.focusedIndex) ?: return
        val category = _state.value.categories.getOrNull(displayIndex) ?: return
        scope.launch { categories.toggleMembership(root, category.id, photo.id) }
    }

    fun createCategory(name: String) {
        if (name.isBlank()) return
        scope.launch { categories.create(root, name) }
    }

    fun renameCategory(id: CategoryId, newName: String) {
        if (newName.isBlank()) return
        scope.launch { categories.rename(root, id, newName) }
    }

    fun deleteCategory(id: CategoryId) {
        scope.launch { categories.delete(root, id) }
    }

    fun exportTxt(destination: Path) {
        scope.launch {
            _state.update { it.copy(isBusy = true, progressLabel = "Writing list…") }
            try {
                val photos = _state.value.photos
                exportTxt.invoke(root, photos, destination)
                _state.update {
                    it.copy(
                        isBusy = false,
                        progressLabel = null,
                        toast = "Saved ${photos.size} entries to ${destination.fileName}",
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
            val photos = _state.value.photos
            _state.update { it.copy(isBusy = true, progressLabel = "0 / ${photos.size}") }
            try {
                val report: CopyReport = copyToFolder.invoke(
                    root = root,
                    photos = photos,
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

    private fun buildReportToast(report: CopyReport): String {
        val parts = mutableListOf("Copied ${report.copied}")
        if (report.skipped > 0) parts += "skipped ${report.skipped}"
        if (report.failed.isNotEmpty()) parts += "${report.failed.size} failed"
        return parts.joinToString(", ")
    }
}
