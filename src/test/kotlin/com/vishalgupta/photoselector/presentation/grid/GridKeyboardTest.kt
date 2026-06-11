package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.common.GroupingMode
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class GridKeyboardTest {

    @get:Rule
    val rule = createComposeRule()

    private val testPhotos = listOf(
        Photo(
            id = PhotoId("a"),
            absolutePath = Path.of("/photos/sunset.jpg"),
            relativePath = "sunset.jpg",
            fileName = "sunset.jpg",
            sizeBytes = 1024,
            lastModifiedEpochMs = 0,
        ),
        Photo(
            id = PhotoId("b"),
            absolutePath = Path.of("/photos/mountain.jpg"),
            relativePath = "mountain.jpg",
            fileName = "mountain.jpg",
            sizeBytes = 2048,
            lastModifiedEpochMs = 0,
        ),
    )

    private val noOpImageLoader = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? = null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun firstArrowPress_fromUnfocused_focusesFirstVisibleTile() {
        val captured = mutableListOf<Int>()
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    GridScreen(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            focusedIndex = -1,
                        ),
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = null,
                        onSetFocusedIndex = { captured += it },
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()

        // The shortcut path fires onSetFocusedIndex(0) and returns, so we expect
        // exactly one emission with index 0 — not a directional-math result.
        assertEquals(listOf(0), captured)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bareDigitKey_togglesNthCustomCategoryAtFocusedTile() {
        val slots = mutableListOf<Int>()
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    GridScreen(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            focusedIndex = 0,
                        ),
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = null,
                        onSetFocusedIndex = {},
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = { slots += it },
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()

        // Bare "2" (no Cmd) toggles the focused tile in the 2nd custom category — slot 1.
        rule.onRoot().performKeyInput { pressKey(Key.Two) }
        rule.waitForIdle()

        assertEquals(listOf(1), slots)
    }

    // --- Multi-select keyboard routing -----------------------------------------------------

    /** Renders the grid with selection callbacks captured; unrelated handlers are no-ops. */
    @Composable
    private fun SelectionGrid(
        state: GridUiState,
        onBack: (() -> Unit)? = null,
        onSelectAll: () -> Unit = {},
        onClearSelection: () -> Unit = {},
        onFileSelectionIntoFavourites: () -> Unit = {},
        onFileSelectionIntoCustom: (Int) -> Unit = {},
        onCompareSelection: (List<Int>, Int) -> Unit = { _, _ -> },
        onSelectionTooLargeToCompare: () -> Unit = {},
    ) {
        GridScreen(
            state = state,
            initialScrollIndex = 0,
            onTileClick = {},
            onChangeFolder = {},
            onSelectCategory = { _, _ -> },
            onCreateCategory = {},
            onRenameCategory = { _, _ -> },
            onDeleteCategory = {},
            onBack = onBack,
            onSetFocusedIndex = {},
            onToggleMembershipAtFocus = {},
            onToggleCustomCategoryAtFocus = {},
            onExportTxt = {},
            onCopyToFolder = {},
            onDismissToast = {},
            imageLoader = noOpImageLoader,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            onFileSelectionIntoFavourites = onFileSelectionIntoFavourites,
            onFileSelectionIntoCustom = onFileSelectionIntoCustom,
            onCompareSelection = onCompareSelection,
            onSelectionTooLargeToCompare = onSelectionTooLargeToCompare,
        )
    }

    private val fourPhotos = (0 until 4).map { i ->
        Photo(
            id = PhotoId("p$i"),
            absolutePath = Path.of("/photos/img$i.jpg"),
            relativePath = "img$i.jpg",
            fileName = "img$i.jpg",
            sizeBytes = 1,
            lastModifiedEpochMs = 0,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cmdA_armsSelectAll() {
        var selectAllCalls = 0
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    SelectionGrid(
                        state = GridUiState(photos = testPhotos, scope = CategoryScope.AllPhotos),
                        onSelectAll = { selectAllCalls++ },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.A) } }
        rule.waitForIdle()

        assertEquals(1, selectAllCalls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun escWithSelection_clearsSelectionInsteadOfPoppingScreen() {
        var cleared = 0
        var backs = 0
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    SelectionGrid(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            selection = setOf(testPhotos[0].id),
                        ),
                        onBack = { backs++ },
                        onClearSelection = { cleared++ },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.Escape) }
        rule.waitForIdle()

        assertEquals(1, cleared)
        assertEquals(0, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fAndDigit_withSelection_fileTheWholeSelection() {
        var favouriteFiles = 0
        val customSlots = mutableListOf<Int>()
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    SelectionGrid(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            selection = setOf(testPhotos[0].id, testPhotos[1].id),
                        ),
                        onFileSelectionIntoFavourites = { favouriteFiles++ },
                        onFileSelectionIntoCustom = { customSlots += it },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.F) }
        rule.onRoot().performKeyInput { pressKey(Key.One) }
        rule.waitForIdle()

        assertEquals(1, favouriteFiles)
        assertTrue("digit files the selection into slot 0", customSlots == listOf(0))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cWithSelection_handsOffTheSelectedIndicesInScopeOrder() {
        var captured: List<Int>? = null
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    SelectionGrid(
                        // Select p1 + p3 (out of order on purpose) -> indices come back ascending.
                        state = GridUiState(
                            photos = fourPhotos,
                            scope = CategoryScope.AllPhotos,
                            selection = setOf(fourPhotos[3].id, fourPhotos[1].id),
                        ),
                        onCompareSelection = { indices, _ -> captured = indices },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.C) }
        rule.waitForIdle()

        assertEquals(listOf(1, 3), captured)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cWithFewerThanTwoSelected_doesNothing() {
        var calls = 0
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    SelectionGrid(
                        state = GridUiState(
                            photos = fourPhotos,
                            scope = CategoryScope.AllPhotos,
                            selection = setOf(fourPhotos[0].id),
                        ),
                        onCompareSelection = { _, _ -> calls++ },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.C) }
        rule.waitForIdle()

        assertEquals("C needs a 2+ selection to open compare/survey", 0, calls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cAboveTheCap_declinesInsteadOfOpening() {
        val thirteen = (0 until 13).map { i ->
            Photo(
                id = PhotoId("p$i"),
                absolutePath = Path.of("/photos/img$i.jpg"),
                relativePath = "img$i.jpg",
                fileName = "img$i.jpg",
                sizeBytes = 1,
                lastModifiedEpochMs = 0,
            )
        }
        var opens = 0
        var declines = 0
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    SelectionGrid(
                        state = GridUiState(
                            photos = thirteen,
                            scope = CategoryScope.AllPhotos,
                            selection = thirteen.map { it.id }.toSet(), // 13 > cap of 12
                        ),
                        onCompareSelection = { _, _ -> opens++ },
                        onSelectionTooLargeToCompare = { declines++ },
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.C) }
        rule.waitForIdle()

        assertEquals("13 selected is over the cap, so nothing opens", 0, opens)
        assertEquals("it declines with feedback instead", 1, declines)
    }

    // --- Mouse-then-keyboard: Enter must not re-fire the mouse-focused tile -----------------------
    // Expanding a burst with the MOUSE leaves Compose focus on that tile. A tile's `clickable`
    // activates on the Enter/Space KEY-UP, so if the key-up leaks past the grid's handler the
    // mouse-focused tile re-opens itself - undoing the keyboard cursor's action. Concretely: open
    // burst A with the mouse, arrow the cursor to burst B, press Enter; without the key-up swallow
    // B opens and then A immediately re-opens ([a0, b0, a0]), so A always wins. The grid owns
    // Enter/Space, so it must swallow their key-ups too.

    // A loader that yields a real (1x1) bitmap, so each tile's Image carries a contentDescription
    // (the file name) and can be located + clicked like a real mouse interaction.
    private val bitmapImageLoader = object : ImageLoader {
        private val onePx = ImageBitmap(1, 1)
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap = onePx
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun mouseExpandedBurst_thenArrowToAnother_enterOpensTheCursorTileNotTheMouseTile() {
        val burstA = PhotoGroup.Burst(listOf(photoNamed("a0"), photoNamed("a1"), photoNamed("a2")))
        val burstB = PhotoGroup.Burst(listOf(photoNamed("b0"), photoNamed("b1"), photoNamed("b2")))
        val groups = listOf(burstA, burstB)
        val toggles = mutableListOf<String>()

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(1000.dp, 800.dp)) {
                    // Stateful host mirroring GridViewModel.toggleBurstExpansion, so the expand actually
                    // reshapes the tiles the way the real screen does.
                    var state by remember {
                        mutableStateOf(
                            GridUiState(
                                photos = burstA.photos + burstB.photos,
                                groups = groups,
                                scope = CategoryScope.AllPhotos,
                                focusedIndex = -1,
                            ),
                        )
                    }
                    GridScreen(
                        state = state,
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = null,
                        onSetFocusedIndex = { idx ->
                            val max = state.displayGroups.size - 1
                            state = state.copy(focusedIndex = idx.coerceIn(-1, max.coerceAtLeast(-1)))
                        },
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = bitmapImageLoader,
                        onToggleBurstExpansion = { id ->
                            toggles += id.value
                            val open = if (state.expandedBurstId == id) null else id
                            val display = displayGroupsFor(state.groups, open)
                            val focus = display.indexOfFirst { it.groupId == id }.takeIf { it >= 0 }
                                ?: state.focusedIndex
                            state = state.copy(
                                expandedBurstId = open,
                                focusedIndex = focus.coerceIn(-1, (display.size - 1).coerceAtLeast(-1)),
                            )
                        },
                    )
                }
            }
        }
        rule.waitForIdle()

        // Mouse-click burst A's key frame (a1, the middle frame of a 3-burst) to expand it.
        rule.onAllNodesWithContentDescription("a1.jpg")[0].performClick()
        rule.waitForIdle()
        assertEquals("the mouse click expands burst A", listOf("a0"), toggles)

        // displayGroups is now [a0, a1, a2, (B)] - arrow the cursor right from a0 to the B tile (index 3).
        repeat(3) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            rule.waitForIdle()
        }
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        // Exactly one further toggle - on B (the cursor tile). No spurious re-open of A from the
        // mouse-focused tile's key-up.
        assertEquals("Enter opens the cursor's burst B once, not the mouse-focused A", listOf("a0", "b0"), toggles)
    }

    // --- Delete must not orphan keyboard focus ---------------------------------------------------
    // A Cmd-click moves Compose's actual focus onto the clicked tile. Deleting that tile removes the
    // focused node, and Compose does not fall focus back to the grid - it orphans, so arrows/Enter
    // stop reaching the grid's key handler. The grid must reclaim focus when the selection clears.
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun deletingTheMouseFocusedTile_keepsArrowKeysWorking() {
        val photos = (0 until 4).map { photoNamed("p$it") }
        val movedTo = mutableListOf<Int>()
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(1000.dp, 800.dp)) {
                    // Stateful host: p1 is pre-selected (the tile we mouse-focus and then delete), and
                    // onDeleteSelection mirrors the view model - drop the selected photo, clear the
                    // selection, reset the cursor.
                    var state by remember {
                        mutableStateOf(
                            GridUiState(
                                photos = photos,
                                groups = photos.map(PhotoGroup::Single),
                                scope = CategoryScope.AllPhotos,
                                focusedIndex = -1,
                                selection = setOf(photos[1].id),
                            ),
                        )
                    }
                    GridScreen(
                        state = state,
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = null,
                        onSetFocusedIndex = { idx ->
                            movedTo += idx
                            state = state.copy(focusedIndex = idx.coerceIn(-1, (state.displayGroups.size - 1).coerceAtLeast(-1)))
                        },
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = bitmapImageLoader,
                        onClearSelection = { state = state.copy(selection = emptySet()) },
                        onDeleteSelection = {
                            val remaining = state.photos.filterNot { it.id in state.selection }
                            state = state.copy(
                                photos = remaining,
                                groups = remaining.map(PhotoGroup::Single),
                                selection = emptySet(),
                                focusedIndex = -1,
                            )
                        },
                    )
                }
            }
        }
        rule.waitForIdle()

        // Mouse-click p1's tile so Compose focus lands on the tile that's about to be deleted.
        rule.onAllNodesWithContentDescription("p1.jpg")[0].performClick()
        rule.waitForIdle()

        // Cmd+Delete arms the move-to-Trash confirm; confirming performs the delete (removing p1).
        rule.onRoot().performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Delete) } }
        rule.waitForIdle()
        rule.onNodeWithText("Move to Trash").performClick()
        rule.waitForIdle()

        // The mouse-focused tile is gone. Without reclaiming focus the grid would be deaf to keys;
        // a first arrow press must still reach the handler and seed the cursor.
        movedTo.clear()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()

        assertTrue("arrow key reached the grid after the focused tile was deleted", movedTo.isNotEmpty())
    }

    // --- Warm return must keep the scrolled position, not snap to the ring -----------------------
    // After arrowing the ring to a tile, then trackpad-scrolling away and opening a tile, returning
    // to the grid (a warm re-entry) must honour the retained scroll - NOT yank the viewport back to
    // the (off-screen) ring. The focus-into-view scroll reacts to cursor moves, not to mounting.
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun warmReturnWithOffscreenRing_keepsRetainedScrollInsteadOfSnappingToRing() {
        val manyPhotos = (0 until 60).map { photoNamed("p$it") }
        lateinit var gridStateRef: LazyGridState
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(400.dp, 400.dp)) {
                    // A retained scroll parked well down the grid (the position the user opened from),
                    // hosted exactly as the real screen does on a warm return.
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = 36)
                    gridStateRef = gridState
                    GridScreen(
                        state = GridUiState(
                            photos = manyPhotos,
                            groups = manyPhotos.map(PhotoGroup::Single),
                            scope = CategoryScope.AllPhotos,
                            focusedIndex = 0, // the ring sits at the top, far above the retained scroll
                        ),
                        initialScrollIndex = 0,
                        retainedGridState = gridState,
                        anchorInitialScroll = false, // warm return: the retained state owns the position
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = null,
                        onSetFocusedIndex = {},
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()

        // The retained scroll stands: it must not have snapped up to the ring at index 0.
        assertTrue(
            "warm return snapped to the ring (index ${gridStateRef.firstVisibleItemIndex}) instead of keeping the scroll",
            gridStateRef.firstVisibleItemIndex > 10,
        )
    }

    // --- Switching the grouping lens must keep the scroll position ------------------------------
    // The LazyGrid keys tiles by photo/group id, so a burst that EXPLODES back into singles keeps its
    // top frame pinned for free (the key survives). The jump happens the other way: the top photo gets
    // ABSORBED into a burst whose representative is a different frame, so the top tile's key VANISHES -
    // the LazyGrid can't retain it and clamps to a random spot. The screen must pin the photo that was
    // at the viewport top by identity, landing on whatever tile now contains it.
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun switchingGroupingLens_keepsTheTopPhotoPinnedInsteadOfJumping() {
        val photos = (0 until 100).map { photoNamed("p$it") }
        // Time grouping collapses two runs into bursts: p0..p49 (one big leading burst) and p55..p64.
        // The top photo we park on, p60, is absorbed into the SECOND burst as a non-representative
        // frame, so its single-tile key (p60) disappears - exactly the case the LazyGrid can't retain.
        val timeGroups: List<PhotoGroup> = buildList {
            add(PhotoGroup.Burst(photos.subList(0, 50)))
            addAll(photos.subList(50, 55).map(PhotoGroup::Single))
            add(PhotoGroup.Burst(photos.subList(55, 65)))
            addAll(photos.subList(65, 100).map(PhotoGroup::Single))
        }
        val modeCalls = mutableListOf<GroupingMode>()
        // The flat photo index reported at the viewport top (column-independent, unlike a tile index).
        var reportedTopFlat = -1
        rule.setContent {
            AppTheme {
                // Wide enough that the toolbar's segmented lens control lays out un-clipped and is hittable.
                Surface(Modifier.size(800.dp, 600.dp)) {
                    // Open in the Off lens (flat singles), parked at p60 - tile 60 == flat 60 here.
                    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = 60)
                    var state by remember {
                        mutableStateOf(
                            GridUiState(
                                photos = photos,
                                groups = photos.map(PhotoGroup::Single),
                                scope = CategoryScope.AllPhotos,
                                groupingMode = GroupingMode.Off,
                                focusedIndex = -1,
                            ),
                        )
                    }
                    GridScreen(
                        state = state,
                        initialScrollIndex = 0,
                        retainedGridState = gridState,
                        anchorInitialScroll = false, // already open: the retained scroll owns the position
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = null,
                        onSetFocusedIndex = {},
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        onFirstVisibleItemChanged = { reportedTopFlat = it },
                        imageLoader = noOpImageLoader,
                        // Mirror the view model: the Time lens collapses the two bursts, Off is flat singles.
                        onSelectGroupingMode = { mode ->
                            modeCalls += mode
                            state = state.copy(
                                groupingMode = mode,
                                groups = if (mode == GroupingMode.Time) timeGroups else photos.map(PhotoGroup::Single),
                            )
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue("retained scroll starts parked on ~p60", reportedTopFlat in 56..64)

        // Flip the lens to Time (label "Bursts") via the toolbar's segmented control.
        rule.onNodeWithContentDescription("Bursts").performClick()
        rule.waitForIdle()
        assertEquals("toolbar click switched the lens to Time", listOf(GroupingMode.Time), modeCalls)

        // p60 is now inside the p55..p64 burst (reported by its first frame, p55). Pinning the top
        // photo must keep the viewport on that run - NOT clamp to the far end of the shorter tile list.
        assertTrue(
            "lens switch jumped the grid (top flat $reportedTopFlat) instead of pinning the top photo's burst",
            reportedTopFlat in 50..64,
        )
    }

    private fun photoNamed(id: String) = Photo(
        id = PhotoId(id),
        absolutePath = Path.of("/photos/$id.jpg"),
        relativePath = "$id.jpg",
        fileName = "$id.jpg",
        sizeBytes = 1,
        lastModifiedEpochMs = 0,
    )
}
