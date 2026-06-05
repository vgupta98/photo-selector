package com.vishalgupta.photoselector.presentation.compare

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which of the two compare panes keyboard filing/zoom-substitution actions currently target. */
enum class ComparePaneSide { LEFT, RIGHT }

/** One side of the compare view: a photo at [index] in scope plus its decode + membership state. */
data class ComparePane(
    val index: Int,
    val photo: Photo?,
    val bitmap: ImageBitmap?,
    val isLoading: Boolean,
    val isFavourite: Boolean,
    val memberships: Set<CategoryId>,
)

data class CompareUiState(
    val left: ComparePane,
    val right: ComparePane,
    val activeSide: ComparePaneSide,
    /** Total photos in the current scope — the denominator for each pane's "n / N" position. */
    val totalInScope: Int,
    val readOnly: Boolean,
    /** All categories for this root, Favourites first — drives the active-pane HUD legend. */
    val categories: List<Category> = emptyList(),
) {
    val active: ComparePane get() = if (activeSide == ComparePaneSide.LEFT) left else right
}

/**
 * Drives the two-up compare view. Both panes load independently through the shared [ImageLoader],
 * but the *zoom transform* is owned by the UI (a single shared `ZoomState`) so pan/zoom stays
 * synchronized across panes — this view model is deliberately blind to it.
 *
 * Filing actions (`F`, digits) target the [CompareUiState.active] pane only; [setActive] / [Tab]
 * switches which. [advanceActive] substitutes the active pane's photo for its neighbour in scope
 * without leaving compare, skipping the other pane so the two never show the same frame.
 */
class CompareViewModel(
    private val root: RootFolder,
    private val photos: List<Photo>,
    leftIndex: Int,
    rightIndex: Int,
    private val categories: CategoriesRepository,
    private val imageLoader: ImageLoader,
    private val isReadOnly: StateFlow<Boolean>,
    parentJob: Job? = null,
) : StateHolder(parentJob) {

    private val categoriesFlow: StateFlow<List<Category>> = categories.observeCategories(root)
    private val membershipsFlow: StateFlow<Map<CategoryId, Set<PhotoId>>> = categories.observeMemberships(root)

    private fun favourites(): Set<PhotoId> = membershipsFlow.value[Category.FAVOURITES_ID].orEmpty()

    private fun membershipsOf(photo: Photo?, members: Map<CategoryId, Set<PhotoId>>): Set<CategoryId> {
        if (photo == null) return emptySet()
        return members.filterValues { photo.id in it }.keys
    }

    private fun paneAt(index: Int): ComparePane {
        val safe = index.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        val photo = photos.getOrNull(safe)
        val members = membershipsFlow.value
        return ComparePane(
            index = safe,
            photo = photo,
            bitmap = null,
            isLoading = photo != null,
            isFavourite = photo != null && photo.id in favourites(),
            memberships = membershipsOf(photo, members),
        )
    }

    private val _state = MutableStateFlow(
        CompareUiState(
            left = paneAt(leftIndex),
            right = paneAt(rightIndex),
            activeSide = ComparePaneSide.LEFT,
            totalInScope = photos.size,
            readOnly = isReadOnly.value,
            categories = categoriesFlow.value,
        ),
    )
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    private var leftLoadJob: Job? = null
    private var rightLoadJob: Job? = null
    private var viewportLongEdgePx: Int = 1600

    init {
        combine(categoriesFlow, membershipsFlow, isReadOnly) { cats, members, readOnly ->
            Triple(cats, members, readOnly)
        }
            .onEach { (cats, members, readOnly) ->
                val favs = members[Category.FAVOURITES_ID].orEmpty()
                _state.update { st ->
                    st.copy(
                        categories = cats,
                        readOnly = readOnly,
                        left = st.left.copy(
                            isFavourite = st.left.photo != null && st.left.photo.id in favs,
                            memberships = membershipsOf(st.left.photo, members),
                        ),
                        right = st.right.copy(
                            isFavourite = st.right.photo != null && st.right.photo.id in favs,
                            memberships = membershipsOf(st.right.photo, members),
                        ),
                    )
                }
            }
            .launchIn(scope)
    }

    fun setViewportLongEdgePx(px: Int) {
        if (px <= 0 || px == viewportLongEdgePx) return
        viewportLongEdgePx = px
        loadPane(ComparePaneSide.LEFT)
        loadPane(ComparePaneSide.RIGHT)
        prefetchAround()
    }

    fun setActive(side: ComparePaneSide) {
        if (_state.value.activeSide == side) return
        _state.update { it.copy(activeSide = side) }
    }

    fun toggleActive() {
        setActive(if (_state.value.activeSide == ComparePaneSide.LEFT) ComparePaneSide.RIGHT else ComparePaneSide.LEFT)
    }

    /**
     * Move the active pane's photo by [delta] within scope, skipping the other pane's photo so the
     * two never collide, and stopping at the ends (no wrap — a culler scans a burst linearly).
     */
    fun advanceActive(delta: Int) {
        if (photos.size <= 1 || delta == 0) return
        val st = _state.value
        val activePane = st.active
        val otherIndex = if (st.activeSide == ComparePaneSide.LEFT) st.right.index else st.left.index
        var candidate = activePane.index + delta
        if (candidate == otherIndex) candidate += delta
        if (candidate !in photos.indices) return

        val photo = photos[candidate]
        val newPane = ComparePane(
            index = candidate,
            photo = photo,
            bitmap = null,
            isLoading = true,
            isFavourite = photo.id in favourites(),
            memberships = membershipsOf(photo, membershipsFlow.value),
        )
        _state.update {
            if (it.activeSide == ComparePaneSide.LEFT) it.copy(left = newPane) else it.copy(right = newPane)
        }
        pinPanes()
        loadPane(st.activeSide)
        prefetchAround()
    }

    /** Toggle the active pane's photo in [categoryId] (F = Favourites, a digit = a custom category). */
    fun toggleCategory(categoryId: CategoryId) {
        val photo = _state.value.active.photo ?: return
        if (categoriesFlow.value.none { it.id == categoryId }) return
        scope.launch { categories.toggleMembership(root, categoryId, photo.id) }
    }

    fun loadIfNeeded() {
        pinPanes()
        if (_state.value.left.bitmap == null) loadPane(ComparePaneSide.LEFT)
        if (_state.value.right.bitmap == null) loadPane(ComparePaneSide.RIGHT)
        prefetchAround()
    }

    /** Index the browser should re-open on when compare exits — the pane the user last acted on. */
    fun exitIndex(): Int = _state.value.active.index

    private fun loadPane(side: ComparePaneSide) {
        val pane = if (side == ComparePaneSide.LEFT) _state.value.left else _state.value.right
        val photo = pane.photo ?: return
        val job = scope.launch {
            val bmp = imageLoader.load(photo, viewportLongEdgePx)
            _state.update { st ->
                val target = if (side == ComparePaneSide.LEFT) st.left else st.right
                if (target.photo?.id != photo.id) return@update st
                val loaded = target.copy(bitmap = bmp, isLoading = false)
                if (side == ComparePaneSide.LEFT) st.copy(left = loaded) else st.copy(right = loaded)
            }
        }
        if (side == ComparePaneSide.LEFT) {
            leftLoadJob?.cancel(); leftLoadJob = job
        } else {
            rightLoadJob?.cancel(); rightLoadJob = job
        }
    }

    /** Keep both visible panes pinned so neither gets evicted while the other decodes. */
    private fun pinPanes() {
        val leftId = _state.value.left.photo?.id
        val rightId = _state.value.right.photo?.id
        imageLoader.unpinAllExcept(leftId)
        if (rightId != null) imageLoader.pin(rightId)
    }

    private fun prefetchAround() {
        if (photos.isEmpty()) return
        val li = _state.value.left.index
        val ri = _state.value.right.index
        val targets = listOfNotNull(
            photos.getOrNull(li + 1),
            photos.getOrNull(li - 1),
            photos.getOrNull(ri + 1),
            photos.getOrNull(ri - 1),
        ).distinctBy { it.id }
        imageLoader.prefetch(targets, viewportLongEdgePx, scope)
    }
}
