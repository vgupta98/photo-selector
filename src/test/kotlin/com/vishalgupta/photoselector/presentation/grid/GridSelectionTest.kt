package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.ui.graphics.ImageBitmap
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadataSource
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import com.vishalgupta.photoselector.domain.repository.PhotoTrash
import com.vishalgupta.photoselector.domain.repository.TrashReport
import com.vishalgupta.photoselector.domain.usecase.CopyPhotosToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportPhotosTxtUseCase
import com.vishalgupta.photoselector.domain.usecase.MovePhotosToTrashUseCase
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.testing.FakeCategoriesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
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

    private val noOpTrash = object : PhotoTrash {
        override suspend fun moveToTrash(photos: List<Photo>): TrashReport =
            TrashReport(trashed = photos.size, failed = emptyList())
    }

    private val deletedRoots = mutableListOf<Set<PhotoId>>()

    // A distinct camera per photo keeps every tile a [PhotoGroup.Single], so a tile index equals a
    // photo index and these selection tests read against the flat list — burst collapsing has its
    // own coverage in BurstGrouperTest and the grid screenshot test.
    private val perPhotoCameraMetadata = CaptureMetadataSource { photo ->
        CaptureMetadata(takenAtEpochMs = null, cameraId = photo.id.value, orientation = null)
    }

    // Same camera + capture times one second apart, so all six photos collapse into one burst when
    // grouping is on - the fixture the toggle test flips on and off.
    private val oneBurstMetadata = CaptureMetadataSource { photo ->
        val i = photo.id.value.removePrefix("p").toInt()
        CaptureMetadata(takenAtEpochMs = i * 1_000L, cameraId = "cam", orientation = 1)
    }

    // single | burst[p1..p4] | single : p0 and p5 sit on their own cameras, p1..p4 share one and
    // shoot a second apart. The burst is tile 1, so collapsing it is a meaningful focus target
    // (distinct from the first/last tile) - the fixture for the collapse-returns-focus test.
    private val singleBurstSingleMetadata = CaptureMetadataSource { photo ->
        when (val i = photo.id.value.removePrefix("p").toInt()) {
            0 -> CaptureMetadata(takenAtEpochMs = 0L, cameraId = "head", orientation = 1)
            5 -> CaptureMetadata(takenAtEpochMs = 0L, cameraId = "tail", orientation = 1)
            else -> CaptureMetadata(takenAtEpochMs = i * 1_000L, cameraId = "burst", orientation = 1)
        }
    }

    // p0|p1 are a two-frame burst (shared camera, a second apart); p2..p5 sit on their own cameras.
    // Deleting one of the pair drops the burst below two frames, so the survivor must regroup to a
    // Single - the fixture for the below-two-frames regroup test.
    private val twoFrameBurstMetadata = CaptureMetadataSource { photo ->
        when (val i = photo.id.value.removePrefix("p").toInt()) {
            0 -> CaptureMetadata(takenAtEpochMs = 0L, cameraId = "pair", orientation = 1)
            1 -> CaptureMetadata(takenAtEpochMs = 1_000L, cameraId = "pair", orientation = 1)
            else -> CaptureMetadata(takenAtEpochMs = 0L, cameraId = "solo$i", orientation = 1)
        }
    }

    private fun viewModel(
        repo: CategoriesRepository,
        trash: PhotoTrash = noOpTrash,
        metadata: CaptureMetadataSource = perPhotoCameraMetadata,
    ): GridViewModel = GridViewModel(
        root = RootFolder(Path.of("/photos")),
        allPhotos = photos,
        categoryScope = CategoryScope.AllPhotos,
        lastViewedPhotoId = null,
        categories = repo,
        exportTxt = ExportPhotosTxtUseCase(noOpExporter),
        copyToFolder = CopyPhotosToFolderUseCase(noOpExporter),
        moveToTrash = MovePhotosToTrashUseCase(trash),
        imageLoader = noOpImageLoader,
        captureMetadataSource = metadata,
        onPhotosDeleted = { ids -> deletedRoots += ids },
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
    fun toggleGroupBursts_collapsesAndExpandsTiles() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = oneBurstMetadata)
        vm.awaitPhotos()
        // Grouping is on by default: the six frames settle into a single burst tile.
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } }
        assertTrue(vm.state.value.groupBursts)

        // Turning it off drops straight back to one tile per photo.
        vm.toggleGroupBursts()
        assertTrue(!vm.state.value.groupBursts)
        assertEquals(photos.size, vm.state.value.groups.size)

        // Turning it back on re-collapses the burst off-thread.
        vm.toggleGroupBursts()
        assertTrue(vm.state.value.groupBursts)
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } }

        vm.onClear()
    }

    @Test
    fun expandBurst_thenFocusFilesOneFrame_whileCollapsedFilesWholeBurst() = runBlocking {
        val repo = FakeCategoriesRepository(categories)
        val vm = viewModel(repo, metadata = oneBurstMetadata)
        vm.awaitPhotos()
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } } // one burst of six

        val burstId = vm.state.value.groups.single().groupId

        // Collapsed: F at the burst tile files all six frames in one additive batch (addMemberships).
        vm.setFocusedIndex(0)
        vm.toggleMembershipAtFocus()
        withTimeout(2_000) { vm.state.first { repo.addCalls.isNotEmpty() } }
        assertEquals(Category.FAVOURITES_ID, repo.addCalls.single().first)
        assertEquals(photos.map { it.id }.toSet(), repo.addCalls.single().second)

        // Expand: now six per-frame tiles, focus on the first frame.
        vm.toggleBurstExpansion(burstId)
        assertEquals(burstId, vm.state.value.expandedBurstId)
        assertEquals(photos.size, vm.state.value.displayGroups.size)
        assertEquals(0, vm.state.value.focusedIndex)

        // Expanded: filing the 3rd frame into "Selects" toggles exactly that one photo (not a batch).
        vm.setFocusedIndex(2)
        vm.toggleCustomCategoryAtFocus(0) // slot 0 == "Selects"
        withTimeout(2_000) { vm.state.first { repo.toggleCalls.isNotEmpty() } }
        assertEquals(selectsId to photos[2].id, repo.toggleCalls.single())

        // Collapse folds back to the single burst tile.
        vm.collapseBurst()
        assertNull(vm.state.value.expandedBurstId)
        assertEquals(1, vm.state.value.displayGroups.size)

        vm.onClear()
    }

    @Test
    fun collapseBurst_returnsFocusToTheBurstTile() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = singleBurstSingleMetadata)
        vm.awaitPhotos()
        // single | burst[p1..p4] | single -> three tiles, the burst in the middle (tile 1).
        withTimeout(2_000) { vm.state.first { it.groups.size == 3 } }
        val burstId = vm.state.value.groups[1].groupId

        // Expand and arrow onto a later frame, away from the burst's leading edge.
        vm.toggleBurstExpansion(burstId)
        assertEquals(burstId, vm.state.value.expandedBurstId)
        vm.setFocusedIndex(3) // p3, deep inside the unfolded run

        // Collapsing returns focus to the burst tile (index 1), not whatever now sits at the old
        // index - symmetric with expansion jumping focus to the first frame.
        vm.collapseBurst()
        assertNull(vm.state.value.expandedBurstId)
        assertEquals(3, vm.state.value.displayGroups.size)
        assertEquals(1, vm.state.value.focusedIndex)
        assertEquals(burstId, vm.state.value.displayGroups[1].groupId)

        vm.onClear()
    }

    @Test
    fun deleteSelection_doesNotRegroupWhenGroupingIsOff() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = oneBurstMetadata)
        vm.awaitPhotos()
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } } // one burst of six

        // User turns grouping off -> one tile per photo.
        vm.toggleGroupBursts()
        withTimeout(2_000) { vm.state.first { it.groups.size == photos.size } }
        assertTrue(!vm.state.value.groupBursts)

        // Delete one frame. With grouping off the delete must leave the tiles flat - it must not
        // re-collapse the remaining frames behind the user's "off" choice.
        vm.toggleSelection(5)
        vm.deleteSelection()
        withTimeout(2_000) { vm.state.first { it.toast != null && !it.isBusy } }

        // Give any (buggy) off-thread regroup a window to fire; assert it never collapses.
        var collapsed = false
        try {
            withTimeout(500) { vm.state.first { it.groups.size < photos.size - 1 } }
            collapsed = true
        } catch (_: TimeoutCancellationException) {
            // No collapse within the window - the correct, grouping-off behaviour.
        }
        assertTrue("delete re-grouped despite grouping being off", !collapsed)
        assertEquals(photos.size - 1, vm.state.value.groups.size)
        assertTrue(!vm.state.value.groupBursts)

        vm.onClear()
    }

    @Test
    fun deleteSelection_regroupsABurstDroppedBelowTwoFramesToASingle() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = twoFrameBurstMetadata)
        vm.awaitPhotos()
        // p0|p1 burst + p2..p5 singles -> 5 tiles, exactly one of them a burst.
        withTimeout(2_000) { vm.state.first { st -> st.groups.count { it is PhotoGroup.Burst } == 1 } }
        val burstId = vm.state.value.groups.first { it is PhotoGroup.Burst }.groupId

        // Expand so the two frames are individually addressable, then delete just the second one (p1).
        vm.toggleBurstExpansion(burstId)
        assertEquals(burstId, vm.state.value.expandedBurstId)
        vm.toggleSelection(1) // display tile 1 == p1, the burst's second frame
        vm.deleteSelection()
        withTimeout(2_000) { vm.state.first { it.toast != null && !it.isBusy } }

        // p1 gone, p0 survives alone: the burst falls below two frames, so it must regroup to a Single
        // (BurstGrouper requires >= 2). No burst remains, and p0 + the four solos are five tiles.
        withTimeout(2_000) { vm.state.first { st -> st.photos.size == photos.size - 1 } }
        withTimeout(2_000) { vm.state.first { st -> st.groups.none { it is PhotoGroup.Burst } } }
        assertTrue(vm.state.value.photos.any { it.id == photos[0].id })
        assertEquals(photos.size - 1, vm.state.value.groups.size)

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

    @Test
    fun deleteSelection_trashesDropsFromListPurgesCategoriesAndNotifiesContainer() = runBlocking {
        val root = RootFolder(Path.of("/photos"))
        val repo = FakeCategoriesRepository(categories)
        // Pre-file two of the about-to-be-deleted photos so we can prove they get purged.
        repo.addMemberships(root, selectsId, setOf(photos[0].id, photos[3].id))
        val vm = viewModel(repo)
        vm.awaitPhotos()

        vm.toggleSelection(0)
        vm.toggleSelection(3)
        vm.deleteSelection()

        withTimeout(2_000) { vm.state.first { it.toast != null && !it.isBusy } }
        val st = vm.state.value
        assertEquals("two photos left the visible list", photos.size - 2, st.photos.size)
        assertTrue(st.photos.none { it.id == photos[0].id || it.id == photos[3].id })
        assertTrue("selection cleared after delete", st.selection.isEmpty())
        assertTrue("toast names the Trash, got: ${st.toast}", st.toast!!.contains("Trash"))

        // Purged from every category, and the container was told which ids went.
        val members = repo.observeMemberships(root).value[selectsId].orEmpty()
        assertTrue(photos[0].id !in members && photos[3].id !in members)
        assertEquals(setOf(photos[0].id, photos[3].id), deletedRoots.flatten().toSet())

        vm.onClear()
    }

    @Test
    fun deleteSelection_keepsPhotosThatFailedToTrash() = runBlocking {
        // Trash everything except the first target, which "fails" (e.g. locked file).
        val failingFirst = object : PhotoTrash {
            override suspend fun moveToTrash(photos: List<Photo>): TrashReport {
                val failed = photos.take(1).map { it to RuntimeException("locked") }
                return TrashReport(trashed = photos.size - failed.size, failed = failed)
            }
        }
        val vm = viewModel(FakeCategoriesRepository(categories), failingFirst)
        vm.awaitPhotos()

        vm.toggleSelection(0)
        vm.toggleSelection(1)
        vm.deleteSelection()

        withTimeout(2_000) { vm.state.first { it.toast != null && !it.isBusy } }
        val st = vm.state.value
        assertEquals("only the successfully trashed photo left", photos.size - 1, st.photos.size)
        assertTrue("the failed photo stays put", st.photos.any { it.id == photos[0].id })
        assertTrue("toast reports the failure, got: ${st.toast}", st.toast!!.contains("failed"))

        vm.onClear()
    }

    @Test
    fun notifySurveyCapExceeded_surfacesAToast() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories))
        vm.awaitPhotos()

        vm.notifySurveyCapExceeded()

        val toast = vm.state.value.toast
        assertTrue("toast names the cap, got: $toast", toast != null && toast.contains("12"))

        vm.onClear()
    }
}
