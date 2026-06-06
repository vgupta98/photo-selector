package com.vishalgupta.photoselector

import androidx.compose.foundation.layout.fillMaxSize
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
import com.vishalgupta.photoselector.presentation.navigation.Screen
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerScreen
import com.vishalgupta.photoselector.presentation.survey.SurveyScreen
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun App(container: AppContainer) {
    val screen by container.currentScreen.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            when (val s = screen) {
                Screen.RootPicker -> {
                    val vm = remember { container.rootPickerViewModel() }
                    RootFolderPickerScreen(vm)
                }
                is Screen.Grid -> key(s) {
                    val vm = remember {
                        container.gridViewModel(s.root, s.scope, s.lastViewedPhotoId)
                    }
                    GridScreen(
                        viewModel = vm,
                        initialScrollIndex = s.initialScrollIndex,
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
                                container.goTo(Screen.RootPicker)
                            }
                        },
                        onSelectCategory = { currentScrollIndex, id ->
                            container.goTo(
                                Screen.Grid(
                                    root = s.root,
                                    scope = CategoryScope.Category(id),
                                    lastViewedPhotoId = s.lastViewedPhotoId,
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
                                            lastViewedPhotoId = s.lastViewedPhotoId,
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
