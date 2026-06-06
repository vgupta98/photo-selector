package com.vishalgupta.photoselector.presentation.survey

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Pure active-tile + filing behaviour on [SurveyViewModel]: the tiles are built from the selected
 * indices, the active tile moves with clamping, and `F`/digit filing targets the active tile only.
 * The VM runs its flow plumbing on the Swing dispatcher, so each assertion that depends on a write
 * awaits the resulting state rather than reading synchronously.
 */
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

    private class FakeCategoriesRepository(initial: List<Category>) : CategoriesRepository {
        private val cats = MutableStateFlow(initial)
        private val members = MutableStateFlow<Map<CategoryId, Set<PhotoId>>>(emptyMap())
        private val readOnly = MutableStateFlow(false)
        val toggleCalls = mutableListOf<Pair<CategoryId, PhotoId>>()

        override fun observeCategories(root: RootFolder): StateFlow<List<Category>> = cats.asStateFlow()
        override fun observeMemberships(root: RootFolder): StateFlow<Map<CategoryId, Set<PhotoId>>> = members.asStateFlow()
        override fun isReadOnly(root: RootFolder): StateFlow<Boolean> = readOnly.asStateFlow()
        override suspend fun create(root: RootFolder, name: String): CategoryId = error("unused")
        override suspend fun rename(root: RootFolder, id: CategoryId, newName: String) {}
        override suspend fun delete(root: RootFolder, id: CategoryId) {}
        override suspend fun toggleMembership(root: RootFolder, id: CategoryId, photo: PhotoId): Boolean {
            toggleCalls += id to photo
            val current = members.value[id].orEmpty()
            val next = if (photo in current) current - photo else current + photo
            members.value = members.value + (id to next)
            return photo in next
        }
        override suspend fun addMemberships(root: RootFolder, id: CategoryId, photos: Collection<PhotoId>): Int = 0
        override suspend fun clearContext() {}
    }

    private fun viewModel(
        repo: CategoriesRepository,
        indices: List<Int> = listOf(1, 3, 4),
        decode: Boolean = false,
    ): SurveyViewModel = SurveyViewModel(
        root = RootFolder(Path.of("/photos")),
        photos = photos,
        indices = indices,
        categories = repo,
        imageLoader = bitmapLoader(decode),
        isReadOnly = MutableStateFlow(false),
    )

    private suspend fun SurveyViewModel.await(predicate: (SurveyUiState) -> Boolean) {
        withTimeout(2_000) { state.first(predicate) }
    }

    @Test
    fun buildsTilesFromSelectedIndices() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), indices = listOf(1, 3, 4))

        val st = vm.state.value
        assertEquals(listOf(1, 3, 4), st.tiles.map { it.index })
        assertEquals(listOf("p1", "p3", "p4"), st.tiles.map { it.photo?.id?.value })
        assertEquals(0, st.activeTile)
        assertEquals(photos.size, st.totalInScope)

        vm.onClear()
    }

    @Test
    fun moveActive_clampsAtBothEnds() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), indices = listOf(1, 3, 4))

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
    fun setActive_ignoresOutOfRange() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), indices = listOf(1, 3, 4))

        vm.setActive(2)
        assertEquals(2, vm.state.value.activeTile)

        vm.setActive(5)
        assertEquals("out-of-range setActive is a no-op", 2, vm.state.value.activeTile)

        vm.onClear()
    }

    @Test
    fun toggleCategory_filesTheActiveTileOnly() = runBlocking {
        val repo = FakeCategoriesRepository(categories)
        val vm = viewModel(repo, indices = listOf(1, 3, 4))

        // Active tile is the first one (p1): favouriting it marks p1, not the others.
        vm.toggleCategory(Category.FAVOURITES_ID)
        vm.await { it.active?.isFavourite == true }

        assertEquals(listOf(Category.FAVOURITES_ID to PhotoId("p1")), repo.toggleCalls)
        val st = vm.state.value
        assertTrue("p1 tile is now favourite", st.tiles[0].isFavourite)
        assertTrue("p3 tile is untouched", !st.tiles[1].isFavourite)

        vm.onClear()
    }

    @Test
    fun toggleCategory_followsTheActiveTileAfterMoving() = runBlocking {
        val repo = FakeCategoriesRepository(categories)
        val vm = viewModel(repo, indices = listOf(1, 3, 4))

        vm.moveActive(1) // active is now tile p3
        vm.toggleCategory(selectsId)
        vm.await { selectsId in (it.active?.memberships ?: emptySet()) }

        assertEquals(selectsId to PhotoId("p3"), repo.toggleCalls.last())

        vm.onClear()
    }

    @Test
    fun loadIfNeeded_decodesEveryTile() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), indices = listOf(1, 3, 4), decode = true)

        vm.loadIfNeeded()
        vm.await { st -> st.tiles.all { it.bitmap != null && !it.isLoading } }

        assertNull(vm.state.value.tiles.firstOrNull { it.bitmap == null })

        vm.onClear()
    }
}
