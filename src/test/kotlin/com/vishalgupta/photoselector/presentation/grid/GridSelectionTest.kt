package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import com.vishalgupta.photoselector.domain.usecase.CopyPhotosToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportPhotosTxtUseCase
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
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
 * Pure selection-model behaviour on [GridViewModel]: the Cmd/Shift/Cmd-A interactions resolve to
 * the right id sets, and a bulk file batches the whole selection into one repository call. The VM
 * runs its flow plumbing on the Swing dispatcher, so each test first waits for the initial slice
 * to populate before driving selection.
 */
class GridSelectionTest {

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

    private val noOpImageLoader = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? = null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    private val noOpExporter = object : PhotoExporter {
        override suspend fun exportTxt(root: RootFolder, favourites: List<Photo>, destinationTxt: Path) {}
        override suspend fun copyToFolder(
            root: RootFolder,
            favourites: List<Photo>,
            destDir: Path,
            policy: ConflictPolicy,
            onProgress: (copied: Int, total: Int) -> Unit,
        ): CopyReport = CopyReport(0, 0, emptyList())
    }

    private class FakeCategoriesRepository(initial: List<Category>) : CategoriesRepository {
        private val cats = MutableStateFlow(initial)
        private val members = MutableStateFlow<Map<CategoryId, Set<PhotoId>>>(emptyMap())
        private val readOnly = MutableStateFlow(false)
        val addCalls = mutableListOf<Pair<CategoryId, Set<PhotoId>>>()

        override fun observeCategories(root: RootFolder): StateFlow<List<Category>> = cats.asStateFlow()
        override fun observeMemberships(root: RootFolder): StateFlow<Map<CategoryId, Set<PhotoId>>> = members.asStateFlow()
        override fun isReadOnly(root: RootFolder): StateFlow<Boolean> = readOnly.asStateFlow()
        override suspend fun create(root: RootFolder, name: String): CategoryId = error("unused")
        override suspend fun rename(root: RootFolder, id: CategoryId, newName: String) {}
        override suspend fun delete(root: RootFolder, id: CategoryId) {}
        override suspend fun toggleMembership(root: RootFolder, id: CategoryId, photo: PhotoId): Boolean = false
        override suspend fun addMemberships(root: RootFolder, id: CategoryId, photos: Collection<PhotoId>): Int {
            addCalls += id to photos.toSet()
            val current = members.value[id].orEmpty()
            val added = photos.filter { it !in current }
            members.value = members.value + (id to (current + added))
            return added.size
        }
        override suspend fun clearContext() {}
    }

    private fun viewModel(repo: CategoriesRepository): GridViewModel = GridViewModel(
        root = RootFolder(Path.of("/photos")),
        allPhotos = photos,
        categoryScope = CategoryScope.AllPhotos,
        lastViewedPhotoId = null,
        categories = repo,
        exportTxt = ExportPhotosTxtUseCase(noOpExporter),
        copyToFolder = CopyPhotosToFolderUseCase(noOpExporter),
        imageLoader = noOpImageLoader,
    )

    private suspend fun GridViewModel.awaitPhotos() {
        withTimeout(2_000) { state.first { it.photos.size == photos.size } }
    }

    @Test
    fun cmdClick_togglesTileAndSetsAnchor() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories))
        vm.awaitPhotos()

        vm.toggleSelection(2)
        assertEquals(setOf(photos[2].id), vm.state.value.selection)
        assertEquals(2, vm.state.value.anchorIndex)

        vm.toggleSelection(2)
        assertTrue(vm.state.value.selection.isEmpty())

        vm.onClear()
    }

    @Test
    fun shiftClick_selectsContiguousRunFromAnchor() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories))
        vm.awaitPhotos()

        vm.toggleSelection(1)   // anchor = 1
        vm.selectRange(4)       // 1..4 inclusive
        assertEquals(setOf(photos[1].id, photos[2].id, photos[3].id, photos[4].id), vm.state.value.selection)
        // Anchor is unchanged, so a second shift-click re-derives the run from the same pivot.
        assertEquals(1, vm.state.value.anchorIndex)
        vm.selectRange(0)       // 0..1
        assertEquals(setOf(photos[0].id, photos[1].id), vm.state.value.selection)

        vm.onClear()
    }

    @Test
    fun selectAll_thenClear() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories))
        vm.awaitPhotos()

        vm.selectAll()
        assertEquals(photos.map { it.id }.toSet(), vm.state.value.selection)

        vm.clearSelection()
        assertTrue(vm.state.value.selection.isEmpty())
        assertNull(vm.state.value.anchorIndex)

        vm.onClear()
    }

    @Test
    fun fileSelectionIntoCustom_batchesWholeSelectionIntoOneCall() = runBlocking {
        val repo = FakeCategoriesRepository(categories)
        val vm = viewModel(repo)
        vm.awaitPhotos()

        vm.toggleSelection(0)
        vm.toggleSelection(3)
        vm.toggleSelection(5)
        vm.fileSelectionIntoCustom(0) // slot 0 == "Selects"

        // The bulk file confirms via a toast; wait for it, then assert the single batched call.
        withTimeout(2_000) { vm.state.first { it.toast != null } }
        assertEquals(1, repo.addCalls.size)
        assertEquals(selectsId, repo.addCalls.single().first)
        assertEquals(setOf(photos[0].id, photos[3].id, photos[5].id), repo.addCalls.single().second)

        vm.onClear()
    }
}
