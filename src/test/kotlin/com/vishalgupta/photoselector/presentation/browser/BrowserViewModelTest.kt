package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository
import com.vishalgupta.photoselector.domain.usecase.ObserveFavouritesUseCase
import com.vishalgupta.photoselector.domain.usecase.ToggleFavouriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BrowserViewModelTest {

    @Test
    fun `default mode is STEP and arrow wraps around end`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos, favourites = emptySet())

        assertEquals(NavigationMode.STEP, vm.state.value.navigationMode)

        vm.next()
        assertEquals(1, vm.state.value.currentIndex)
        vm.next()
        assertEquals(2, vm.state.value.currentIndex)
        vm.next()
        assertEquals(0, vm.state.value.currentIndex) // wraps in STEP
    }

    @Test
    fun `SKIP_DECIDED next walks over favourited photos forward`() {
        val photos = makePhotos(5)
        val vm = makeViewModel(
            photos,
            favourites = setOf(photos[1].id, photos[2].id),
        )
        vm.toggleNavigationMode()
        assertEquals(NavigationMode.SKIP_DECIDED, vm.state.value.navigationMode)

        vm.next() // from 0, skip 1 and 2, land on 3
        assertEquals(3, vm.state.value.currentIndex)
    }

    @Test
    fun `SKIP_DECIDED previous walks over favourited photos backward`() {
        val photos = makePhotos(5)
        val vm = makeViewModel(
            photos,
            favourites = setOf(photos[1].id, photos[2].id),
            initialIndex = 4,
        )
        vm.toggleNavigationMode()

        vm.previous() // from 4, skip 2, skip 1, land on 0 (skip 3 only if rated, but 3 is undecided so land on 3)
        assertEquals(3, vm.state.value.currentIndex)
    }

    @Test
    fun `SKIP_DECIDED forward with no more undecided stays put`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(
            photos,
            favourites = setOf(photos[1].id, photos[2].id),
        )
        vm.toggleNavigationMode()

        assertEquals(0, vm.state.value.currentIndex)
        vm.next() // starting at 0, forward search begins at index 1 → all decided
        assertEquals(0, vm.state.value.currentIndex)
    }

    @Test
    fun `Shift step always advances by one even in SKIP_DECIDED`() {
        val photos = makePhotos(5)
        val vm = makeViewModel(
            photos,
            favourites = setOf(photos[1].id, photos[2].id),
        )
        vm.toggleNavigationMode()

        vm.nextStep() // forced step from 0 → 1, even though 1 is favourited
        assertEquals(1, vm.state.value.currentIndex)
        vm.nextStep()
        assertEquals(2, vm.state.value.currentIndex)
    }

    @Test
    fun `toggling navigation mode when all photos are decided stays in STEP`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(
            photos,
            favourites = setOf(photos[0].id, photos[1].id, photos[2].id),
        )

        vm.toggleNavigationMode()
        assertEquals(
            NavigationMode.STEP,
            vm.state.value.navigationMode,
            "Should silently stay in STEP when all photos are already decided",
        )
    }

    @Test
    fun `cursor stays put when favourites change in SKIP_DECIDED`() {
        // The brief: "Toggling a rating while in SKIP mode doesn't auto-advance —
        // the user explicitly hits → to move."
        val favRepo = FakeFavouritesRepository(initial = emptySet())
        val photos = makePhotos(3)
        val vm = makeViewModelWith(photos, favRepo)
        vm.toggleNavigationMode()
        assertEquals(NavigationMode.SKIP_DECIDED, vm.state.value.navigationMode)

        // Simulate the user favouriting the current photo (index 0) from elsewhere.
        favRepo.set(setOf(photos[0].id))
        // The viewmodel's combine() onEach updates isCurrentFavourite asynchronously via
        // Dispatchers.Swing; what we care about here is that the cursor itself did not move.
        assertEquals(0, vm.state.value.currentIndex)
        assertSame(photos[0], vm.state.value.currentPhoto)
    }

    // ----- helpers -----

    private fun makePhotos(n: Int): List<Photo> = (0 until n).map { i ->
        Photo(
            id = PhotoId("p$i"),
            absolutePath = Paths.get("/tmp/p$i.jpg"),
            relativePath = "p$i.jpg",
            fileName = "p$i.jpg",
            sizeBytes = 1,
            lastModifiedEpochMs = 0,
        )
    }

    private fun makeViewModel(
        photos: List<Photo>,
        favourites: Set<PhotoId>,
        initialIndex: Int = 0,
    ): BrowserViewModel {
        val repo = FakeFavouritesRepository(favourites)
        return makeViewModelWith(photos, repo, initialIndex)
    }

    private fun makeViewModelWith(
        photos: List<Photo>,
        repo: FakeFavouritesRepository,
        initialIndex: Int = 0,
    ): BrowserViewModel {
        val root = RootFolder(Paths.get("/tmp"))
        return BrowserViewModel(
            root = root,
            photos = photos,
            initialIndex = initialIndex,
            observeFavourites = ObserveFavouritesUseCase(repo),
            toggleFavourite = ToggleFavouriteUseCase(repo),
            imageLoader = NoopImageLoader,
            isReadOnly = MutableStateFlow(false).asStateFlow(),
        )
    }

    private class FakeFavouritesRepository(initial: Set<PhotoId>) : FavouritesRepository {
        private val flow = MutableStateFlow(initial)
        private val readOnly = MutableStateFlow(false)

        fun set(value: Set<PhotoId>) { flow.value = value }

        override fun observe(root: RootFolder): StateFlow<Set<PhotoId>> = flow.asStateFlow()
        override fun isReadOnly(root: RootFolder): StateFlow<Boolean> = readOnly.asStateFlow()

        override suspend fun toggle(root: RootFolder, id: PhotoId): Boolean {
            val current = flow.value
            val nowFav = id !in current
            flow.value = if (nowFav) current + id else current - id
            return nowFav
        }

        override suspend fun clearContext() {
            flow.value = emptySet()
        }
    }

    private object NoopImageLoader : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? = null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int) = Unit
        override fun evictAll() = Unit
        override fun pin(id: PhotoId) = Unit
        override fun unpinAllExcept(id: PhotoId?) = Unit
    }
}
