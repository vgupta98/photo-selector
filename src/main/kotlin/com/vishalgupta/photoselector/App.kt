package com.vishalgupta.photoselector

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.di.AppContainer
import com.vishalgupta.photoselector.presentation.browser.BrowserScreen
import com.vishalgupta.photoselector.presentation.favourites.FavouritesScreen
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
                is Screen.Browser -> {
                    val vm = remember(s.root.path, s.initialIndex) {
                        container.browserViewModel(s.root, s.initialIndex)
                    }
                    BrowserScreen(
                        viewModel = vm,
                        onOpenFavourites = { currentIndex ->
                            container.goTo(Screen.Favourites(s.root, returnIndex = currentIndex))
                        },
                        onChangeFolder = {
                            coroutineScope.launch {
                                container.resetForNewRoot()
                                container.goTo(Screen.RootPicker)
                            }
                        },
                    )
                }
                is Screen.Favourites -> {
                    val vm = remember(s.root.path) { container.favouritesViewModel(s.root) }
                    FavouritesScreen(
                        viewModel = vm,
                        onBack = {
                            container.goTo(Screen.Browser(s.root, initialIndex = s.returnIndex))
                        },
                        onOpenPhoto = { photo ->
                            val index = container.photosFor(s.root).indexOfFirst { it.id == photo.id }
                                .coerceAtLeast(0)
                            container.goTo(Screen.Browser(s.root, initialIndex = index))
                        },
                    )
                }
            }
        }
    }
}
