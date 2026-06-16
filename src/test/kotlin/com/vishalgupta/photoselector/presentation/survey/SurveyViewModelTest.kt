package com.vishalgupta.photoselector.presentation.survey

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.testing.FakeCategoriesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Pure active-tile + filing behaviour on [SurveyViewModel]: the tiles are built from the selected
 * indices, the active tile moves with clamping, and `F`/digit filing targets the active tile only.
 * The VM runs its flow plumbing on the injected dispatcher; each test drives it with a
 * [StandardTestDispatcher] and `advanceUntilIdle()`, so writes settle in virtual time rather than on
 * a real-clock `withTimeout` poll.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyViewModelTest {

    private val photos = (0 until 6).map { i ->
        Photo(
            id = PhotoId("p$i"),
            absolutePath = Path.of("/photos/img$i.jpg"),
            relativePath = "img$i.jpg",
            fileName = "img$i.jpg",
            sizeBytes = 1,
            lastModifiedEpochMs = 0,
        )
    }
    private val selectsId = CategoryId("selects")
    private val categories = listOf(Category.favourites(), Category(selectsId, "Selects", builtIn = false))

    private fun bitmapLoader(decode: Boolean) = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? =
            if (decode) ImageBitmap(8, 8) else null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    private fun viewModel(
        repo: CategoriesRepository,
        dispatcher: TestDispatcher,
        indices: List<Int> = listOf(1, 3, 4),
        decode: Boolean = false,
    ): SurveyViewModel = SurveyViewModel(
        root = RootFolder(Path.of("/photos")),
        photos = photos,
        indices = indices,
        categories = repo,
        imageLoader = bitmapLoader(decode),
        isReadOnly = MutableStateFlow(false),
        dispatcher = dispatcher,
    )

    @Test
    fun buildsTilesFromSelectedIndices() = runTest {
        val vm = viewModel(FakeCategoriesRepository(categories), StandardTestDispatcher(testScheduler), indices = listOf(1, 3, 4))
        advanceUntilIdle()

        val st = vm.state.value
        assertEquals(listOf(1, 3, 4), st.tiles.map { it.index })
        assertEquals(listOf("p1", "p3", "p4"), st.tiles.map { it.photo?.id?.value })
        assertEquals(0, st.activeTile)
        assertEquals(photos.size, st.totalInScope)

        vm.onClear()
    }

    @Test
    fun moveActive_clampsAtBothEnds() = runTest {
        val vm = viewModel(FakeCategoriesRepository(categories), StandardTestDispatcher(testScheduler), indices = listOf(1, 3, 4))
        advanceUntilIdle()

        vm.moveActive(-1)
        assertEquals("can't move before the first tile", 0, vm.state.value.activeTile)

        vm.moveActive(1)
        assertEquals(1, vm.state.value.activeTile)

        vm.moveActive(10)
        assertEquals("clamps to the last tile", 2, vm.state.value.activeTile)

        vm.moveActive(1)
        assertEquals("can't move past the last tile", 2, vm.state.value.activeTile)

        vm.onClear()
    }

    @Test
    fun setActive_ignoresOutOfRange() = runTest {
        val vm = viewModel(FakeCategoriesRepository(categories), StandardTestDispatcher(testScheduler), indices = listOf(1, 3, 4))
        advanceUntilIdle()

        vm.setActive(2)
        assertEquals(2, vm.state.value.activeTile)

        vm.setActive(5)
        assertEquals("out-of-range setActive is a no-op", 2, vm.state.value.activeTile)

        vm.onClear()
    }

    @Test
    fun toggleCategory_filesTheActiveTileOnly() = runTest {
        val repo = FakeCategoriesRepository(categories)
        val vm = viewModel(repo, StandardTestDispatcher(testScheduler), indices = listOf(1, 3, 4))
        advanceUntilIdle()

        // Active tile is the first one (p1): favouriting it marks p1, not the others.
        vm.toggleCategory(Category.FAVOURITES_ID)
        advanceUntilIdle()
        assertTrue("p1 became favourite", vm.state.value.active?.isFavourite == true)

        assertEquals(listOf(Category.FAVOURITES_ID to PhotoId("p1")), repo.toggleCalls)
        val st = vm.state.value
        assertTrue("p1 tile is now favourite", st.tiles[0].isFavourite)
        assertTrue("p3 tile is untouched", !st.tiles[1].isFavourite)

        vm.onClear()
    }

    @Test
    fun toggleCategory_followsTheActiveTileAfterMoving() = runTest {
        val repo = FakeCategoriesRepository(categories)
        val vm = viewModel(repo, StandardTestDispatcher(testScheduler), indices = listOf(1, 3, 4))
        advanceUntilIdle()

        vm.moveActive(1) // active is now tile p3
        vm.toggleCategory(selectsId)
        advanceUntilIdle()
        assertTrue("p3 joined the Selects category", selectsId in (vm.state.value.active?.memberships ?: emptySet()))

        assertEquals(selectsId to PhotoId("p3"), repo.toggleCalls.last())

        vm.onClear()
    }

    @Test
    fun reportingTheViewport_decodesEveryTile() = runTest {
        val vm = viewModel(FakeCategoriesRepository(categories), StandardTestDispatcher(testScheduler), indices = listOf(1, 3, 4), decode = true)
        advanceUntilIdle()

        // No decode happens until the real per-tile viewport is reported.
        assertNull("no bitmap before the viewport is known", vm.state.value.tiles.firstOrNull { it.bitmap != null })

        vm.setViewportLongEdgePx(512)
        advanceUntilIdle()
        assertTrue("every tile decoded", vm.state.value.tiles.all { it.bitmap != null && !it.isLoading })

        assertNull(vm.state.value.tiles.firstOrNull { it.bitmap == null })

        vm.onClear()
    }
}
