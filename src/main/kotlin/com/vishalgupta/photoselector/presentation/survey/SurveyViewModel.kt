package com.vishalgupta.photoselector.presentation.survey

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing

/** One survey tile: a photo at [index] in scope plus its decode + membership state. */
data class SurveyTile(
    val index: Int,
    val photo: Photo?,
    val bitmap: ImageBitmap?,
    val isLoading: Boolean,
    val isFavourite: Boolean,
    val memberships: Set<CategoryId>,
)

data class SurveyUiState(
    val tiles: List<SurveyTile>,
    /** Position within [tiles] of the active tile — what `F`/digits file and the border highlights. */
    val activeTile: Int,
    /** Total photos in scope — the denominator for each tile's "n / N" position label. */
    val totalInScope: Int,
    val readOnly: Boolean,
    /** All categories for this root, Favourites first — drives the active-tile HUD legend. */
    val categories: List<Category> = emptyList(),
) {
    val active: SurveyTile? get() = tiles.getOrNull(activeTile)
}

/**
 * Drives the survey overview: 3+ tiles picked out in the grid, shown fit-to-cell with no zoom, for
 * a fast "which of these keepers" pass. One tile is [SurveyUiState.active]; [setActive] / [moveActive]
 * change which, and filing actions (`F`, digits) target it only — the same active-target model as
 * compare, minus the synchronized zoom (an overview doesn't pixel-peep). Each tile decodes
 * independently through the shared [ImageLoader] at the small per-tile viewport the UI reports.
 */
class SurveyViewModel(
    private val root: RootFolder,
    private val photos: List<Photo>,
    indices: List<Int>,
    private val categories: CategoriesRepository,
    private val imageLoader: ImageLoader,
    private val isReadOnly: StateFlow<Boolean>,
    parentJob: Job? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Swing,
) : StateHolder(parentJob, dispatcher) {

    private val categoriesFlow: StateFlow<List<Category>> = categories.observeCategories(root)
    private val membershipsFlow: StateFlow<Map<CategoryId, Set<PhotoId>>> = categories.observeMemberships(root)

    private fun favourites(): Set<PhotoId> = membershipsFlow.value[Category.FAVOURITES_ID].orEmpty()

    private fun membershipsOf(photo: Photo?, members: Map<CategoryId, Set<PhotoId>>): Set<CategoryId> {
        if (photo == null) return emptySet()
        return members.filterValues { photo.id in it }.keys
    }

    private fun tileAt(index: Int): SurveyTile {
        val safe = index.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
        val photo = photos.getOrNull(safe)
        return SurveyTile(
            index = safe,
            photo = photo,
            bitmap = null,
            isLoading = photo != null,
            isFavourite = photo != null && photo.id in favourites(),
            memberships = membershipsOf(photo, membershipsFlow.value),
        )
    }

    private val _state = MutableStateFlow(
        SurveyUiState(
            tiles = indices.map { tileAt(it) },
            activeTile = 0,
            totalInScope = photos.size,
            readOnly = isReadOnly.value,
            categories = categoriesFlow.value,
        ),
    )
    val state: StateFlow<SurveyUiState> = _state.asStateFlow()

    private val loadJobs = mutableMapOf<Int, Job>()
    // 0 = viewport not reported yet. Decoding waits for the real per-tile size so we don't decode
    // every tile once at a guessed default and immediately throw it away (a survey can be 12 tiles).
    private var viewportLongEdgePx: Int = 0

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
                        tiles = st.tiles.map { tile ->
                            tile.copy(
                                isFavourite = tile.photo != null && tile.photo.id in favs,
                                memberships = membershipsOf(tile.photo, members),
                            )
                        },
                    )
                }
            }
            .launchIn(scope)
    }

    /**
     * Report the real per-tile viewport and (re)decode at it. This is the only decode trigger: the
     * first call — once the layout reports a size — pins every tile and decodes them at their actual
     * cell size; later calls (a window resize) re-decode at the new size.
     */
    fun setViewportLongEdgePx(px: Int) {
        if (px <= 0 || px == viewportLongEdgePx) return
        viewportLongEdgePx = px
        pinTiles()
        _state.value.tiles.indices.forEach { loadTile(it) }
    }

    fun setActive(pos: Int) {
        _state.update { if (pos in it.tiles.indices) it.copy(activeTile = pos) else it }
    }

    /**
     * Move the active tile by [delta] through the flat tile list, clamped at the ends (no wrap). The
     * screen passes ±1 for left/right and ±cols for up/down: vertical movement is a flat index jump,
     * not column-aware, so on a ragged last row `↓` can clamp to the current tile. Intentional — the
     * survey is a small at-a-glance grid, and `Tab` always reaches every tile.
     */
    fun moveActive(delta: Int) {
        _state.update { st ->
            if (st.tiles.isEmpty()) return@update st
            st.copy(activeTile = (st.activeTile + delta).coerceIn(0, st.tiles.size - 1))
        }
    }

    /** Toggle the active tile's photo in [categoryId] (F = Favourites, a digit = a custom category). */
    fun toggleCategory(categoryId: CategoryId) {
        val photo = _state.value.active?.photo ?: return
        if (categoriesFlow.value.none { it.id == categoryId }) return
        scope.launch { categories.toggleMembership(root, categoryId, photo.id) }
    }

    private fun loadTile(pos: Int) {
        val photo = _state.value.tiles.getOrNull(pos)?.photo ?: return
        loadJobs[pos]?.cancel()
        loadJobs[pos] = scope.launch {
            val bmp = imageLoader.load(photo, viewportLongEdgePx)
            _state.update { st ->
                val target = st.tiles.getOrNull(pos) ?: return@update st
                if (target.photo?.id != photo.id) return@update st
                val tiles = st.tiles.toMutableList().also { it[pos] = target.copy(bitmap = bmp, isLoading = false) }
                st.copy(tiles = tiles)
            }
        }
    }

    /** Pin every visible tile so decoding one can't evict another from the LRU cache. */
    private fun pinTiles() {
        val ids = _state.value.tiles.mapNotNull { it.photo?.id }
        imageLoader.unpinAllExcept(ids.firstOrNull())
        ids.drop(1).forEach { imageLoader.pin(it) }
    }
}
