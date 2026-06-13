package com.vishalgupta.photoselector

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.di.AppContainer
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.presentation.browser.BrowserScreen
import com.vishalgupta.photoselector.presentation.compare.CompareScreen
import com.vishalgupta.photoselector.presentation.grid.GridScreen
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.navigation.GridRetentionKey
import com.vishalgupta.photoselector.presentation.navigation.Screen
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerScreen
import com.vishalgupta.photoselector.presentation.survey.SurveyScreen
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun App(container: AppContainer) {
    val screen by container.currentScreen.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll position retained per (root, scope) for the session, alongside the retained view models
    // in the container. Held here (not inside the Grid branch, which leaves composition on every
    // navigation away) so a Grid -> Browser -> Grid round trip returns to the exact same scroll.
    // Cleared on a root change, in lock-step with the container dropping its retained grids.
    val gridScrollStates = remember { mutableMapOf<GridRetentionKey, LazyGridState>() }

    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            when (val s = screen) {
                Screen.RootPicker -> {
                    val vm = remember { container.rootPickerViewModel() }
                    RootFolderPickerScreen(vm)
                }
                is Screen.Grid -> key(GridRetentionKey(s.root.path, s.scope)) {
                    val retentionKey = GridRetentionKey(s.root.path, s.scope)
                    // Warm if this (root, scope) grid was shown before this visit: it then reuses its
                    // retained scroll. Latched per visit (remember) so it can't flip mid-visit once the
                    // getOrPut below inserts the key. A cold first visit re-anchors to initialScrollIndex.
                    val anchorInitialScroll = remember { retentionKey !in gridScrollStates }
                    // Seed a cold first visit's scroll at the restored FLAT index (tile == flat while the
                    // grid is still ungrouped singles, which is exactly the loading state) rather than at 0
                    // and chasing it: that closes the window where the grid sits at 0 and persists 0 before
                    // the re-pin runs, wiping the saved resume point. The grid re-pins by identity once
                    // bursts form. A warm return reuses the already-correct retained state untouched.
                    val gridState = remember {
                        gridScrollStates.getOrPut(retentionKey) {
                            LazyGridState(firstVisibleItemIndex = s.initialScrollIndex.coerceAtLeast(0))
                        }
                    }
                    val vm = remember {
                        container.gridViewModel(s.root, s.scope, s.lastViewedPhotoId).also {
                            // The deliberate "Show in All Photos" jump seats the keyboard ring on the
                            // revealed photo so it's unmistakable among thousands (the scroll-into-view
                            // itself is driven by revealPhotoId below). Done in the remember initializer,
                            // NOT a post-mount effect: seating focus changes focusedIndex, and doing that
                            // after the grid mounts re-keys the reveal's reconcile effect and cancels its
                            // scroll mid-animation. Setting it before the grid first composes sidesteps
                            // that race. A same-scope resume sets focusRevealedPhoto = false and so leaves
                            // the (possibly absent) ring alone. Mirrors the last-viewed refresh on retrieval.
                            if (s.focusRevealedPhoto) it.focusPhoto(s.revealPhotoId)
                        }
                    }
                    GridScreen(
                        viewModel = vm,
                        initialScrollIndex = s.initialScrollIndex,
                        gridState = gridState,
                        anchorInitialScroll = anchorInitialScroll,
                        revealPhotoId = s.revealPhotoId,
                        onTileClick = { index ->
                            container.goTo(
                                Screen.Browser(
                                    root = s.root,
                                    initialIndex = index,
                                    scope = vm.state.value.scope,
                                    returnScrollIndex = s.returnScrollIndex,
                                ),
                            )
                        },
                        onChangeFolder = {
                            coroutineScope.launch {
                                container.resetForNewRoot()
                                gridScrollStates.clear()
                                container.goTo(Screen.RootPicker)
                            }
                        },
                        onSelectCategory = { currentScrollIndex, id ->
                            container.goTo(
                                Screen.Grid(
                                    root = s.root,
                                    scope = CategoryScope.Category(id),
                                    // No lastViewedPhotoId: a category's underline marks what was browsed
                                    // *in that category*, not whatever was last opened from All Photos.
                                    returnScrollIndex = currentScrollIndex,
                                ),
                            )
                        },
                        onCompareSelection = { indices, returnScrollIndex ->
                            val scope = vm.state.value.scope
                            when {
                                indices.size == 2 -> container.goTo(
                                    Screen.Compare(
                                        root = s.root,
                                        scope = scope,
                                        leftIndex = indices[0],
                                        rightIndex = indices[1],
                                        returnScrollIndex = returnScrollIndex,
                                        returnToGrid = true,
                                    ),
                                )
                                indices.size >= 3 -> container.goTo(
                                    Screen.Survey(
                                        root = s.root,
                                        scope = scope,
                                        indices = indices,
                                        returnScrollIndex = returnScrollIndex,
                                    ),
                                )
                            }
                        },
                        onBack = when (s.scope) {
                            CategoryScope.AllPhotos -> null
                            is CategoryScope.Category -> {
                                {
                                    container.goTo(
                                        Screen.Grid(
                                            root = s.root,
                                            scope = CategoryScope.AllPhotos,
                                            initialScrollIndex = s.returnScrollIndex
                                                ?: container.loadBrowsePosition(s.root).lastIndex,
                                            // No lastViewedPhotoId / revealPhotoId: backing out of a
                                            // category must NOT drag the category-browsed photo into All
                                            // Photos and scroll there - categories are viewed separately.
                                            // All Photos returns to its own retained scroll; use the
                                            // browser's "Show in All Photos" for a deliberate jump.
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
                is Screen.Browser -> {
                    val vm = remember(s.root.path, s.initialIndex, s.scope) {
                        container.browserViewModel(s.root, s.initialIndex, s.scope)
                    }
                    LaunchedEffect(vm) {
                        vm.state.collect { state ->
                            container.currentPhotoPath.value = state.currentPhoto?.absolutePath
                        }
                    }
                    BrowserScreen(
                        viewModel = vm,
                        systemActions = container.systemActions,
                        onOpenFavourites = {
                            container.goTo(
                                Screen.Grid(
                                    s.root,
                                    CategoryScope.Category(Category.FAVOURITES_ID),
                                    lastViewedPhotoId = vm.state.value.currentPhoto?.id,
                                ),
                            )
                        },
                        onChangeFolder = {
                            coroutineScope.launch {
                                container.resetForNewRoot()
                                gridScrollStates.clear()
                                container.goTo(Screen.RootPicker)
                            }
                        },
                        onBack = {
                            val idx = vm.state.value.currentIndex
                            val photoId = vm.state.value.currentPhoto?.id
                            container.goTo(
                                Screen.Grid(
                                    s.root,
                                    s.scope,
                                    initialScrollIndex = idx,
                                    lastViewedPhotoId = photoId,
                                    // Resume the grid on the photo just browsed, ring or no ring (mouse-only
                                    // users get the same resume keyboard users always did). Same scope, so
                                    // no ring is spawned - focusRevealedPhoto stays false.
                                    revealPhotoId = photoId,
                                    returnScrollIndex = s.returnScrollIndex,
                                ),
                            )
                        },
                        onCompare = {
                            val st = vm.state.value
                            if (st.photos.size >= 2) {
                                container.goTo(
                                    Screen.Compare(
                                        root = s.root,
                                        scope = s.scope,
                                        leftIndex = st.currentIndex,
                                        rightIndex = (st.currentIndex + 1) % st.photos.size,
                                        returnScrollIndex = s.returnScrollIndex,
                                    ),
                                )
                            }
                        },
                        // Only offered when browsing a category: jump to this photo in the All Photos grid,
                        // ringing it there. Null in the All-Photos browser (it's already All Photos), which
                        // is what hides the button and disables the key.
                        onShowInAllPhotos = (s.scope as? CategoryScope.Category)?.let {
                            {
                                container.goTo(
                                    Screen.Grid(
                                        root = s.root,
                                        scope = CategoryScope.AllPhotos,
                                        revealPhotoId = vm.state.value.currentPhoto?.id,
                                        focusRevealedPhoto = true,
                                    ),
                                )
                            }
                        },
                    )
                }
                is Screen.Compare -> {
                    val vm = remember(s.root.path, s.leftIndex, s.rightIndex, s.scope) {
                        container.compareViewModel(s.root, s.scope, s.leftIndex, s.rightIndex)
                    }
                    CompareScreen(
                        viewModel = vm,
                        systemActions = container.systemActions,
                        onExit = {
                            // A grid-originated compare returns to the grid it came from; a
                            // browser-originated one drops back into the full-screen browser.
                            if (s.returnToGrid) {
                                container.goTo(
                                    Screen.Grid(
                                        root = s.root,
                                        scope = s.scope,
                                        initialScrollIndex = s.returnScrollIndex ?: 0,
                                    ),
                                )
                            } else {
                                container.goTo(
                                    Screen.Browser(
                                        root = s.root,
                                        initialIndex = vm.exitIndex(),
                                        scope = s.scope,
                                        returnScrollIndex = s.returnScrollIndex,
                                    ),
                                )
                            }
                        },
                    )
                }
                is Screen.Survey -> key(s) {
                    val vm = remember { container.surveyViewModel(s.root, s.scope, s.indices) }
                    SurveyScreen(
                        viewModel = vm,
                        systemActions = container.systemActions,
                        onExit = {
                            container.goTo(
                                Screen.Grid(
                                    root = s.root,
                                    scope = s.scope,
                                    initialScrollIndex = s.returnScrollIndex ?: 0,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
