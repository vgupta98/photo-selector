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
import com.vishalgupta.photoselector.presentation.common.CategoryToggle
import com.vishalgupta.photoselector.presentation.common.customCategories
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.navigation.activeCategoryId
import com.vishalgupta.photoselector.presentation.navigation.slice
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
    // Transient multi-select: the tiles the mouse (Cmd/Shift-click, Cmd+A) has picked out for a
    // bulk action. Never persisted; cleared on Esc, scope change, or screen exit. [anchorIndex]
    // is the pivot a Shift-click range extends from.
    val selection: Set<PhotoId> = emptySet(),
    val anchorIndex: Int? = null,
    val isBusy: Boolean = false,
    val progressLabel: String? = null,
    val toast: String? = null,
) {
    /** Photos in the built-in Favourites — what the tile star indicates, in any scope. */
    val markedIds: Set<PhotoId> get() = memberships[Category.FAVOURITES_ID].orEmpty()

    /** True while a multi-select is active — drives the top-bar swap and bulk key routing. */
    val hasSelection: Boolean get() = selection.isNotEmpty()
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

    // One-shot confirmation that a keyboard toggle landed — the persistent star/badge on the
    // tile shows the resulting state, this names the action. Same event the browser emits.
    private val _toggleEvents = Channel<CategoryToggle>(Channel.BUFFERED)
    val toggleEvents: Flow<CategoryToggle> = _toggleEvents.receiveAsFlow()

    private var scrollSaveJob: Job? = null
    private var lastKnownIndex: Int? = null

    init {
        combine(
            categories.observeCategories(root),
            categories.observeMemberships(root),
        ) { cats, members -> cats to members }
            .onEach { (cats, members) ->
                val photos = categoryScope.slice(allPhotos, members[categoryScope.activeCategoryId].orEmpty())
                val validIds = photos.mapTo(HashSet()) { it.id }
                _state.update {
                    it.copy(
                        photos = photos,
                        categories = cats,
                        memberships = members,
                        focusedIndex = it.focusedIndex.coerceIn(-1, (photos.size - 1).coerceAtLeast(-1)),
                        // Drop any selected tiles the new slice no longer contains, so a bulk
                        // action can never touch a photo that has scrolled out of scope.
                        selection = if (it.selection.isEmpty()) it.selection else it.selection.intersect(validIds),
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

    /** Toggles the focused photo's Favourites membership (F / Space, in any scope). */
    fun toggleMembershipAtFocus() {
        val photo = _state.value.photos.getOrNull(_state.value.focusedIndex) ?: return
        scope.launch {
            val added = categories.toggleMembership(root, Category.FAVOURITES_ID, photo.id)
            _toggleEvents.send(CategoryToggle(Category.FAVOURITES_NAME, isFavourite = true, added = added))
        }
    }

    /** Toggles the focused photo in the Nth custom category (bare digit 1..9), if one exists. */
    fun toggleCustomCategoryAtFocus(slot: Int) {
        val photo = _state.value.photos.getOrNull(_state.value.focusedIndex) ?: return
        val category = _state.value.categories.customCategories().getOrNull(slot) ?: return
        scope.launch {
            val added = categories.toggleMembership(root, category.id, photo.id)
            _toggleEvents.send(CategoryToggle(category.name, isFavourite = false, added = added))
        }
    }

    // ---- Multi-select ----------------------------------------------------------------------
    // Mouse-driven (Cmd/Shift-click, Cmd+A) so the established plain-click-opens-the-browser
    // gesture is preserved. The set lives in state and is screenshot-testable directly.

    /** Cmd+Click: flip one tile in the selection; that tile becomes the range anchor. */
    fun toggleSelection(index: Int) {
        val photo = _state.value.photos.getOrNull(index) ?: return
        _state.update {
            val next = if (photo.id in it.selection) it.selection - photo.id else it.selection + photo.id
            it.copy(selection = next, anchorIndex = index)
        }
    }

    /** Shift+Click: select the contiguous run from the anchor to [index]; anchor unchanged. */
    fun selectRange(index: Int) {
        _state.update { st ->
            if (index !in st.photos.indices) return@update st
            val anchor = (st.anchorIndex ?: index).coerceIn(st.photos.indices)
            val lo = minOf(anchor, index)
            val hi = maxOf(anchor, index)
            val ids = (lo..hi).mapNotNull { st.photos.getOrNull(it)?.id }.toSet()
            st.copy(selection = ids, anchorIndex = anchor)
        }
    }

    /** Cmd+A: select every photo in the current scope. */
    fun selectAll() {
        _state.update { it.copy(selection = it.photos.mapTo(HashSet()) { p -> p.id }) }
    }

    /** Esc / Clear: drop the selection. */
    fun clearSelection() {
        _state.update { it.copy(selection = emptySet(), anchorIndex = null) }
    }

    /** Files the whole selection into Favourites (the selection bar's star, or F when active). */
    fun fileSelectionIntoFavourites() =
        fileSelectionInto(Category.FAVOURITES_ID, Category.FAVOURITES_NAME)

    /** Files the whole selection into the Nth custom category (a digit while a selection is active). */
    fun fileSelectionIntoCustom(slot: Int) {
        val category = _state.value.categories.customCategories().getOrNull(slot) ?: return
        fileSelectionInto(category.id, category.name)
    }

    private fun fileSelectionInto(id: CategoryId, name: String) {
        val ids = _state.value.selection
        if (ids.isEmpty()) return
        scope.launch {
            val added = categories.addMemberships(root, id, ids)
            _state.update { it.copy(toast = bulkFileToast(name, requested = ids.size, added = added)) }
        }
    }

    /** Copies just the selected photos into [destination] (the selection bar's "Copy selected…"). */
    fun copySelectionTo(destination: Path, policy: ConflictPolicy) {
        val ids = _state.value.selection
        copyPhotos(_state.value.photos.filter { it.id in ids }, destination, policy)
    }

    private fun bulkFileToast(category: String, requested: Int, added: Int): String = when {
        added == 0 -> "All $requested already in $category"
        added == requested -> "Added $requested to $category"
        else -> "Added $added to $category ($requested selected)"
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
        copyPhotos(_state.value.photos, destination, policy)
    }

    private fun copyPhotos(photos: List<Photo>, destination: Path, policy: ConflictPolicy) {
        if (photos.isEmpty()) return
        scope.launch {
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
