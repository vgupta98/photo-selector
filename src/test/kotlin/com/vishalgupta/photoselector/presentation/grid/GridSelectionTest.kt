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
import com.vishalgupta.photoselector.presentation.common.GroupingMode
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.testing.FakeCategoriesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        initialGroupingMode: GroupingMode = GroupingMode.Time,
        onGroupingModeChanged: ((GroupingMode) -> Unit)? = null,
        hasSeenSimilarityCoachmark: Boolean = true,
        onSimilarityCoachmarkSeen: () -> Unit = {},
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
        initialGroupingMode = initialGroupingMode,
        hasSeenSimilarityCoachmark = hasSeenSimilarityCoachmark,
        onSimilarityCoachmarkSeen = onSimilarityCoachmarkSeen,
        onGroupingModeChanged = onGroupingModeChanged,
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
    fun setLastViewed_reseatsAnExistingRingOntoThatPhoto() = runBlocking {
        // Off keeps one tile per photo, so a tile index equals a photo index here.
        val vm = viewModel(FakeCategoriesRepository(categories), initialGroupingMode = GroupingMode.Off)
        vm.awaitPhotos()
        vm.setFocusedIndex(1) // a ring is showing at tile 1

        vm.setLastViewed(photos[4].id) // returned from the viewer on p4

        assertEquals("the ring follows the mouse-driven open to p4", 4, vm.state.value.focusedIndex)
        assertEquals(photos[4].id, vm.state.value.lastViewedPhotoId)
        vm.onClear()
    }

    @Test
    fun setLastViewed_doesNotSpawnARingForAPureMouseUser() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), initialGroupingMode = GroupingMode.Off)
        vm.awaitPhotos() // no ring: focusedIndex stays at its -1 default

        vm.setLastViewed(photos[4].id)

        assertEquals("no ring present, so none is spawned", -1, vm.state.value.focusedIndex)
        assertEquals("the underline marker still moves", photos[4].id, vm.state.value.lastViewedPhotoId)
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
    fun groupingMode_opensInTheSeededLens() = runBlocking {
        // A rebuilt grid (Grid -> Browser -> Grid) is constructed with the session's last lens.
        val vm = viewModel(FakeCategoriesRepository(categories), initialGroupingMode = GroupingMode.Similarity)
        vm.awaitPhotos()
        assertEquals(GroupingMode.Similarity, vm.state.value.groupingMode)
        vm.onClear()
    }

    @Test
    fun setGroupingMode_reportsTheChangeUpwards() = runBlocking {
        // The container listens here to remember the choice, so the next rebuilt grid reopens in it.
        var reported: GroupingMode? = null
        val vm = viewModel(FakeCategoriesRepository(categories), onGroupingModeChanged = { reported = it })
        vm.awaitPhotos()

        vm.setGroupingMode(GroupingMode.Off)
        assertEquals(GroupingMode.Off, reported)
        assertEquals(GroupingMode.Off, vm.state.value.groupingMode)

        // A no-op re-select of the current lens reports nothing new.
        reported = null
        vm.setGroupingMode(GroupingMode.Off)
        assertEquals(null, reported)

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
    fun regroupCompletingAfterGroupingToggledOff_doesNotReapplyBursts() = runBlocking {
        // Same-camera burst metadata, but the first read blocks on a gate, so the off-thread grouping
        // pass is guaranteed in-flight when we toggle grouping off - the race that finding 2 describes.
        val gate = java.util.concurrent.CountDownLatch(1)
        val gatedBurstMetadata = CaptureMetadataSource { photo ->
            gate.await()
            val i = photo.id.value.removePrefix("p").toInt()
            CaptureMetadata(takenAtEpochMs = i * 1_000L, cameraId = "cam", orientation = 1)
        }
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = gatedBurstMetadata)
        vm.awaitPhotos()

        // Grouping is on by default, so a regroup is already launched and now blocked on the gate.
        // Toggle off before releasing it: singles stand, and cancel() can't stop the blocked job.
        vm.toggleGroupBursts()
        assertTrue(!vm.state.value.groupBursts)
        assertEquals(photos.size, vm.state.value.groups.size)

        gate.countDown() // let the now-stale regroup finish and attempt its _state.update

        // Give that update a window to (wrongly) collapse the tiles; it must not.
        var collapsed = false
        try {
            withTimeout(500) { vm.state.first { it.groups.size < photos.size } }
            collapsed = true
        } catch (_: TimeoutCancellationException) {
            // No collapse within the window - the in-flight regroup correctly bailed on groupBursts.
        }
        assertTrue("a stale regroup re-applied bursts after grouping was toggled off", !collapsed)
        assertTrue(!vm.state.value.groupBursts)
        assertEquals(photos.size, vm.state.value.groups.size)

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
    fun deleteSelection_keepsFocusOnTheSamePhotoByIdentity() = runBlocking {
        // Lens Off, so no regroup runs after the delete to fix up a bare-index focus. Deleting a photo
        // *before* the cursor renumbers the tiles, and focus must follow the photo it was on - not stay
        // a coerced index that now points at a neighbour (the identity-refocus invariant removePhotos
        // already honours; the in-grid delete must match it).
        val vm = viewModel(FakeCategoriesRepository(categories), initialGroupingMode = GroupingMode.Off)
        vm.awaitPhotos()
        withTimeout(2_000) { vm.state.first { it.groups.size == photos.size } } // six singles

        vm.setFocusedIndex(4) // cursor on p4
        assertEquals(photos[4].id, vm.state.value.displayGroups[4].keyPhoto.id)

        vm.toggleSelection(1) // select p1, an earlier tile
        vm.deleteSelection()
        withTimeout(2_000) { vm.state.first { it.toast != null && !it.isBusy } }

        val st = vm.state.value
        assertEquals(listOf("p0", "p2", "p3", "p4", "p5"), st.photos.map { it.id.value })
        assertEquals("focus stays on p4, now one tile earlier", photos[4].id, st.displayGroups[st.focusedIndex].keyPhoto.id)

        vm.onClear()
    }

    @Test
    fun removePhotos_dropsExternallyTrashedFramesAndKeepsFocusByIdentity() = runBlocking {
        // The grid is now retained across navigation, so a delete made in the browser (which used to
        // be picked up by rebuilding the grid) is instead pushed in via removePhotos. The trashed
        // frame must leave the list and focus must stay on the *photo* it was on, not a bare index.
        val vm = viewModel(FakeCategoriesRepository(categories))
        vm.awaitPhotos()
        vm.setFocusedIndex(3) // every photo is its own tile, so focus sits on p3
        assertEquals(photos[3].id, vm.state.value.displayGroups[vm.state.value.focusedIndex].keyPhoto.id)

        vm.removePhotos(setOf(photos[1].id)) // a browser delete of p1, propagated to this retained grid

        val st = vm.state.value
        assertEquals(listOf("p0", "p2", "p3", "p4", "p5"), st.photos.map { it.id.value })
        assertEquals("focus stays on p3, now one tile earlier", photos[3].id, st.displayGroups[st.focusedIndex].keyPhoto.id)

        // A no-op when the ids aren't present (the grid that performed the delete already pruned itself).
        vm.removePhotos(setOf(photos[1].id))
        assertEquals(5, vm.state.value.photos.size)

        vm.onClear()
    }

    @Test
    fun regroupCollapsing_keepsFocusOnTheSamePhotosTile() = runBlocking {
        // The user's bug: focus a photo, switch to a grouping lens, and when grouping lands ~a beat
        // (or a minute) later the tiles renumber under the cursor. Focus must follow the *photo*, not
        // stay a bare index that now points at a different burst (which made Enter expand the wrong one).
        val vm = viewModel(
            FakeCategoriesRepository(categories),
            metadata = singleBurstSingleMetadata,
            initialGroupingMode = GroupingMode.Off,
        )
        vm.awaitPhotos()
        // Off: one tile per photo. Focus p2 (tile 2), which will fold *into* the burst.
        vm.setFocusedIndex(2)
        assertEquals(photos[2].id, vm.state.value.displayGroups[2].keyPhoto.id)

        // Switch to Time: p1..p4 collapse to one burst -> [p0, burst(p1..p4), p5] = 3 tiles.
        vm.setGroupingMode(GroupingMode.Time)
        withTimeout(2_000) { vm.state.first { it.groups.size == 3 } }

        // Focus rode the reshape onto the burst tile (index 1) that now contains p2 - not the bare,
        // coerced old index 2 (which would be p5, a different tile entirely).
        assertEquals(1, vm.state.value.focusedIndex)
        assertTrue(vm.state.value.displayGroups[1].photos.any { it.id == photos[2].id })

        vm.onClear()
    }

    @Test
    fun switchingLensOff_keepsFocusOnTheSamePhotosTile() = runBlocking {
        // The other direction: from a collapsed view, turning grouping off unfolds bursts back to
        // singles and renumbers tiles *upward*. Focus on a tile past the burst must track its photo.
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = singleBurstSingleMetadata)
        vm.awaitPhotos()
        // Time (default): [p0, burst(p1..p4), p5] -> 3 tiles. Focus p5 (the last tile, index 2).
        withTimeout(2_000) { vm.state.first { it.groups.size == 3 } }
        vm.setFocusedIndex(2)
        assertEquals(photos[5].id, vm.state.value.displayGroups[2].keyPhoto.id)

        // Off: back to six singles. p5 is now tile 5, not the coerced old index 2 (which would be p2).
        vm.setGroupingMode(GroupingMode.Off)
        assertEquals(photos.size, vm.state.value.displayGroups.size)
        assertEquals(5, vm.state.value.focusedIndex)
        assertEquals(photos[5].id, vm.state.value.displayGroups[5].keyPhoto.id)

        vm.onClear()
    }

    @Test
    fun grouping_seedsThenClearsProgress() = runBlocking {
        // A grouping pass exposes determinate progress that the grid surfaces as a non-blocking bar,
        // and clears it when done. Gate the metadata read so the pass is provably in flight first.
        val gate = java.util.concurrent.CountDownLatch(1)
        val gatedMetadata = CaptureMetadataSource { photo ->
            gate.await()
            val i = photo.id.value.removePrefix("p").toInt()
            CaptureMetadata(takenAtEpochMs = i * 1_000L, cameraId = "cam", orientation = 1)
        }
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = gatedMetadata)
        vm.awaitPhotos()

        // Grouping is on by default, so a pass is launched and blocked on the gate: progress is seeded.
        withTimeout(2_000) { vm.state.first { it.grouping != null } }
        assertEquals(photos.size, vm.state.value.grouping!!.total)

        // Let it finish: the bursts land and progress clears.
        gate.countDown()
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } }
        withTimeout(2_000) { vm.state.first { it.grouping == null } }

        vm.onClear()
    }

    @Test
    fun grouping_warmPassDoesNotFlashTheProgressBar() = runBlocking {
        // A warm pass (instant metadata, e.g. a memoized Time re-slice) finishes inside the grace
        // window, so the determinate bar must never appear - a flashed-then-gone bar is just noise.
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = oneBurstMetadata, initialGroupingMode = GroupingMode.Off)
        vm.awaitPhotos()
        withTimeout(2_000) { vm.state.first { it.groups.size == photos.size } } // Off: six singles

        var flashed = false
        val watcher = launch { vm.state.collect { if (it.grouping != null) flashed = true } }

        vm.setGroupingMode(GroupingMode.Time) // instant metadata -> a sub-grace pass
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } } // the burst landed
        delay(GROUPING_BAR_GRACE_MS + 150) // give a grace-armed bar its chance to (wrongly) fire
        watcher.cancel()

        assertTrue("a warm regroup flashed the progress bar", !flashed)
        vm.onClear()
    }

    @Test
    fun groupingOutcome_firesOnceOnAUserLensPickWithPayoffCounts() = runBlocking {
        // oneBurstMetadata collapses all six frames into one burst. A deliberate lens pick must announce
        // the result exactly once, with counts derived from the applied groups.
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = oneBurstMetadata, initialGroupingMode = GroupingMode.Off)
        vm.awaitPhotos()

        vm.setGroupingMode(GroupingMode.Time)
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } } // the burst landed (state applied first)
        val outcome = withTimeout(2_000) { vm.groupingOutcomes.first() }

        assertEquals(GroupingMode.Time, outcome.mode)
        assertEquals(1, outcome.burstCount)
        assertEquals(photos.size, outcome.photosInBursts)
        vm.onClear()
    }

    @Test
    fun groupingOutcome_isEmptyWhenALensPickProducesNoBursts() = runBlocking {
        // perPhotoCameraMetadata never groups, so picking a lens yields zero stacks: the outcome still
        // fires (burstCount == 0) so the grid can explain the empty result rather than going silent.
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = perPhotoCameraMetadata, initialGroupingMode = GroupingMode.Off)
        vm.awaitPhotos()

        vm.setGroupingMode(GroupingMode.Time)
        val outcome = withTimeout(2_000) { vm.groupingOutcomes.first() }

        assertEquals(0, outcome.burstCount)
        assertEquals(0, outcome.photosInBursts)
        vm.onClear()
    }

    @Test
    fun groupingOutcome_doesNotFireOnTheSeededFirstLoadPass() = runBlocking {
        // A grid seeded into a lens regroups on init (announce = false): a background pass, not a user
        // pick, so it must stay silent — otherwise every folder-open would pop a summary.
        val vm = viewModel(FakeCategoriesRepository(categories), metadata = oneBurstMetadata, initialGroupingMode = GroupingMode.Time)
        vm.awaitPhotos()
        withTimeout(2_000) { vm.state.first { it.groups.size == 1 } } // init pass collapsed the burst

        var fired = false
        val watcher = launch { vm.groupingOutcomes.collect { fired = true } }
        delay(200)
        watcher.cancel()

        assertTrue("the seeded first-load pass must not announce", !fired)
        vm.onClear()
    }

    @Test
    fun similarityCoachmark_showsOnFirstPickThenDismissPersistsAndNeverReturns() = runBlocking {
        var seen = 0
        val vm = viewModel(
            FakeCategoriesRepository(categories),
            hasSeenSimilarityCoachmark = false,
            onSimilarityCoachmarkSeen = { seen++ },
        )
        vm.awaitPhotos()
        assertTrue("no coachmark before the lens is picked", !vm.state.value.showSimilarityCoachmark)

        vm.setGroupingMode(GroupingMode.Similarity)
        assertTrue("coachmark shows on the first Similar pick", vm.state.value.showSimilarityCoachmark)

        vm.dismissSimilarityCoachmark()
        assertTrue("dismissing hides it", !vm.state.value.showSimilarityCoachmark)
        assertEquals("dismissal is persisted exactly once", 1, seen)

        // Re-picking Similar (after switching away) never shows it again.
        vm.setGroupingMode(GroupingMode.Off)
        vm.setGroupingMode(GroupingMode.Similarity)
        assertTrue("coachmark never returns once dismissed", !vm.state.value.showSimilarityCoachmark)
        assertEquals("no second persist call", 1, seen)
        vm.onClear()
    }

    @Test
    fun similarityCoachmark_neverShowsWhenAlreadySeen() = runBlocking {
        val vm = viewModel(FakeCategoriesRepository(categories), hasSeenSimilarityCoachmark = true)
        vm.awaitPhotos()
        vm.setGroupingMode(GroupingMode.Similarity)
        assertTrue("a returning user is not re-coached", !vm.state.value.showSimilarityCoachmark)
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
