package com.vishalgupta.photoselector

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.di.AppContainer
import com.vishalgupta.photoselector.presentation.browser.BrowserScreen
import com.vishalgupta.photoselector.presentation.grid.GridScreen
import com.vishalgupta.photoselector.presentation.navigation.BrowseScope
import com.vishalgupta.photoselector.presentation.navigation.Screen
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerScreen
import com.vishalgupta.photoselector.presentation.theme.AppTheme
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
                is Screen.Grid -> {
                    val vm = remember(s.root.path, s.scope) {
                        container.gridViewModel(s.root, s.scope)
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
                                ),
                            )
                        },
                        onChangeFolder = {
                            coroutineScope.launch {
                                container.resetForNewRoot()
                                container.goTo(Screen.RootPicker)
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
                            container.goTo(Screen.Grid(s.root, BrowseScope.FavouritesOnly))
                        },
                        onChangeFolder = {
                            coroutineScope.launch {
                                container.resetForNewRoot()
                                container.goTo(Screen.RootPicker)
                            }
                        },
                        onBack = {
                            val idx = vm.state.value.currentIndex
                            container.goTo(Screen.Grid(s.root, s.scope, initialScrollIndex = idx))
                        },
                    )
                }
            }
        }
    }
}
