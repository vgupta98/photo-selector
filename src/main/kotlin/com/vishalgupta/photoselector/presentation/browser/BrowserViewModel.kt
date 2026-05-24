package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.usecase.ObserveFavouritesUseCase
import com.vishalgupta.photoselector.domain.usecase.ToggleFavouriteUseCase
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

data class BrowserUiState(
    val photos: List<Photo>,
    val currentIndex: Int,
    val currentPhoto: Photo?,
    val currentBitmap: ImageBitmap?,
    val isLoadingBitmap: Boolean,
    val isCurrentFavourite: Boolean,
    val favouriteCount: Int,
    val readOnly: Boolean,
) {
    companion object {
        fun initial(photos: List<Photo>) = BrowserUiState(
            photos = photos,
            currentIndex = 0,
            currentPhoto = photos.firstOrNull(),
            currentBitmap = null,
            isLoadingBitmap = photos.isNotEmpty(),
            isCurrentFavourite = false,
            favouriteCount = 0,
            readOnly = false,
        )
    }
}

class BrowserViewModel(
    private val root: RootFolder,
    private val photos: List<Photo>,
    private val initialIndex: Int,
    private val observeFavourites: ObserveFavouritesUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val imageLoader: ImageLoader,
    private val isReadOnly: StateFlow<Boolean>,
) : StateHolder() {

    private val favouritesFlow: StateFlow<Set<PhotoId>> = observeFavourites(root)

    private val _state = MutableStateFlow(
        run {
            val safeIndex = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            val firstPhoto = photos.getOrNull(safeIndex)
            BrowserUiState.initial(photos).copy(
                currentIndex = safeIndex,
                currentPhoto = firstPhoto,
                isCurrentFavourite = firstPhoto != null && firstPhoto.id in favouritesFlow.value,
                favouriteCount = favouritesFlow.value.size,
            )
        },
    )
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _toggleEvents = Channel<Boolean>(Channel.BUFFERED)
    val toggleEvents: Flow<Boolean> = _toggleEvents.receiveAsFlow()

    private var loadJob: Job? = null
    private var viewportLongEdgePx: Int = 1600

    init {
        combine(favouritesFlow, isReadOnly) { favs, readOnly -> favs to readOnly }
            .onEach { (favs, readOnly) ->
                _state.update {
                    it.copy(
                        isCurrentFavourite = it.currentPhoto != null && it.currentPhoto.id in favs,
                        favouriteCount = favs.size,
                        readOnly = readOnly,
                    )
                }
            }
            .launchIn(scope)
    }

    fun setViewportLongEdgePx(px: Int) {
        if (px <= 0 || px == viewportLongEdgePx) return
        viewportLongEdgePx = px
        loadCurrent()
        prefetchAround()
    }

    fun next() = jumpTo(_state.value.currentIndex + 1)
    fun previous() = jumpTo(_state.value.currentIndex - 1)

    fun jumpTo(index: Int) {
        if (photos.isEmpty()) return
        val bounded = ((index % photos.size) + photos.size) % photos.size
        if (bounded == _state.value.currentIndex && _state.value.currentBitmap != null) return
        val photo = photos[bounded]
        _state.update {
            it.copy(
                currentIndex = bounded,
                currentPhoto = photo,
                currentBitmap = null,
                isLoadingBitmap = true,
                isCurrentFavourite = photo.id in favouritesFlow.value,
            )
        }
        imageLoader.unpinAllExcept(photo.id)
        imageLoader.pin(photo.id)
        loadCurrent()
        prefetchAround()
    }

    fun toggleFavourite() {
        val photo = _state.value.currentPhoto ?: return
        scope.launch {
            val nowFavourite = toggleFavourite(root, photo.id)
            _toggleEvents.trySend(nowFavourite)
        }
    }

    private fun loadCurrent() {
        loadJob?.cancel()
        val photo = _state.value.currentPhoto ?: return
        loadJob = scope.launch {
            val bmp = imageLoader.load(photo, viewportLongEdgePx)
            _state.update {
                if (it.currentPhoto?.id == photo.id) {
                    it.copy(currentBitmap = bmp, isLoadingBitmap = false)
                } else {
                    it
                }
            }
        }
    }

    private fun prefetchAround() {
        if (photos.isEmpty()) return
        val idx = _state.value.currentIndex
        val targets = listOfNotNull(
            photos.getOrNull(idx + 1),
            photos.getOrNull(idx - 1),
            photos.getOrNull(idx + 2),
            photos.getOrNull(idx + 3),
        )
        imageLoader.prefetch(targets, viewportLongEdgePx, scope)
    }

    fun loadIfNeeded() {
        if (_state.value.currentBitmap == null && _state.value.currentPhoto != null) {
            loadCurrent()
            prefetchAround()
        }
    }

    fun photoIdAtCurrent(): PhotoId? = _state.value.currentPhoto?.id
}
