package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.browser.BrowserScreen
import com.vishalgupta.photoselector.presentation.browser.BrowserUiState
import com.vishalgupta.photoselector.presentation.browser.FavouriteToastState
import com.vishalgupta.photoselector.presentation.favourites.FavouritesScreen
import com.vishalgupta.photoselector.presentation.favourites.FavouritesUiState
import com.vishalgupta.photoselector.presentation.grid.GridScreen
import com.vishalgupta.photoselector.presentation.grid.GridUiState
import com.vishalgupta.photoselector.presentation.navigation.BrowseScope
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerScreen
import com.vishalgupta.photoselector.presentation.rootpicker.RootPickerUiState
import com.vishalgupta.photoselector.presentation.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class ScreenSplitScreenshotTest {

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

    // --- RootFolderPickerScreen ---

    @Test
    fun rootPicker_idle() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(600.dp, 400.dp)) {
                    RootFolderPickerScreen(
                        state = RootPickerUiState(),
                        onPickFolder = {},
                        onCancelScan = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("root-picker-idle")
    }

    @Test
    fun rootPicker_scanning() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(600.dp, 400.dp)) {
                    RootFolderPickerScreen(
                        state = RootPickerUiState(
                            phase = RootPickerUiState.Phase.Scanning,
                            scanned = 142,
                            found = 37,
                        ),
                        onPickFolder = {},
                        onCancelScan = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("root-picker-scanning")
    }

    @Test
    fun rootPicker_failed() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(600.dp, 400.dp)) {
                    RootFolderPickerScreen(
                        state = RootPickerUiState(
                            phase = RootPickerUiState.Phase.Failed,
                            errorMessage = "Permission denied",
                        ),
                        onPickFolder = {},
                        onCancelScan = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("root-picker-failed")
    }

    // --- FavouritesScreen ---

    @Test
    fun favourites_empty() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    FavouritesScreen(
                        state = FavouritesUiState(),
                        onBack = {},
                        onOpenPhoto = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("favourites-empty")
    }

    @Test
    fun favourites_busy() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    FavouritesScreen(
                        state = FavouritesUiState(
                            favourites = testPhotos,
                            isBusy = true,
                            progressLabel = "1 / 2",
                        ),
                        onBack = {},
                        onOpenPhoto = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("favourites-busy")
    }

    // --- GridScreen ---

    @Test
    fun grid_allPhotos() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    GridScreen(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = BrowseScope.AllPhotos,
                            favouriteIds = setOf(PhotoId("a")),
                            lastViewedPhotoId = PhotoId("b"),
                        ),
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onToggleScope = {},
                        onSetFocusedIndex = {},
                        onToggleFavouriteAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("grid-all-photos")
    }

    @Test
    fun grid_favouritesOnly() {
        val favPhotos = testPhotos.take(1)
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    GridScreen(
                        state = GridUiState(
                            photos = favPhotos,
                            scope = BrowseScope.FavouritesOnly,
                            favouriteIds = setOf(PhotoId("a")),
                        ),
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onToggleScope = {},
                        onSetFocusedIndex = {},
                        onToggleFavouriteAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("grid-favourites-only")
    }

    @Test
    fun grid_empty() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    GridScreen(
                        state = GridUiState(),
                        initialScrollIndex = 0,
                        onTileClick = {},
                        onChangeFolder = {},
                        onToggleScope = {},
                        onSetFocusedIndex = {},
                        onToggleFavouriteAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = noOpImageLoader,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("grid-empty")
    }

    // --- BrowserScreen ---

    @Test
    fun browser_loading() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    BrowserScreen(
                        state = BrowserUiState(
                            photos = testPhotos,
                            currentIndex = 0,
                            currentPhoto = testPhotos[0],
                            currentBitmap = null,
                            isLoadingBitmap = true,
                            isCurrentFavourite = false,
                            favouriteCount = 0,
                            readOnly = false,
                        ),
                        toast = null,
                        onPrevious = {},
                        onNext = {},
                        onToggleFavourite = {},
                        onViewportSizeChanged = {},
                        onOpenFavourites = {},
                        onChangeFolder = {},
                        onBackToGrid = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("browser-loading")
    }

    @Test
    fun browser_empty() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    BrowserScreen(
                        state = BrowserUiState.initial(emptyList()),
                        toast = null,
                        onPrevious = {},
                        onNext = {},
                        onToggleFavourite = {},
                        onViewportSizeChanged = {},
                        onOpenFavourites = {},
                        onChangeFolder = {},
                        onBackToGrid = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("browser-empty")
    }

    @Test
    fun browser_withToast() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    BrowserScreen(
                        state = BrowserUiState(
                            photos = testPhotos,
                            currentIndex = 0,
                            currentPhoto = testPhotos[0],
                            currentBitmap = ImageBitmap(200, 150),
                            isLoadingBitmap = false,
                            isCurrentFavourite = true,
                            favouriteCount = 1,
                            readOnly = false,
                        ),
                        toast = FavouriteToastState(isFavourite = true),
                        onPrevious = {},
                        onNext = {},
                        onToggleFavourite = {},
                        onViewportSizeChanged = {},
                        onOpenFavourites = {},
                        onChangeFolder = {},
                        onBackToGrid = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("browser-with-toast")
    }
}
