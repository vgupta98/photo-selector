package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.browser.BrowserScreen
import com.vishalgupta.photoselector.presentation.browser.BrowserUiState
import com.vishalgupta.photoselector.presentation.browser.FavouriteToastState
import com.vishalgupta.photoselector.presentation.grid.GridScreen
import com.vishalgupta.photoselector.presentation.grid.GridUiState
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerScreen
import com.vishalgupta.photoselector.presentation.rootpicker.RootPickerUiState
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class ScreenSplitScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val selectsId = CategoryId("selects")
    private val categories = listOf(Category.favourites(), Category(selectsId, "Selects", builtIn = false))

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

    @androidx.compose.runtime.Composable
    private fun Grid(state: GridUiState, onBack: (() -> Unit)?) {
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
            onToggleCategoryAtFocus = {},
            onExportTxt = {},
            onCopyToFolder = {},
            onDismissToast = {},
            imageLoader = noOpImageLoader,
        )
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

    // --- GridScreen ---

    @Test
    fun grid_allPhotos() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            categories = categories,
                            memberships = mapOf(Category.FAVOURITES_ID to setOf(PhotoId("a"))),
                            lastViewedPhotoId = PhotoId("b"),
                        ),
                        onBack = null,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("grid-all-photos")
    }

    @Test
    fun grid_categoryMenuOpen() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            categories = categories,
                            memberships = mapOf(
                                Category.FAVOURITES_ID to setOf(PhotoId("a")),
                                selectsId to setOf(PhotoId("b")),
                            ),
                        ),
                        onBack = null,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Categories (2)").performClick()
        rule.waitForIdle()
        // Each category shows with its leading Cmd-shortcut digit, count, and the
        // "New category…" affordance.
        rule.onNodeWithText("1  Favourites  (1)").assertIsDisplayed()
        rule.onNodeWithText("2  Selects  (1)").assertIsDisplayed()
        rule.onNodeWithText("New category…").assertIsDisplayed()
        // The menu renders in its own popup root; capture that, not the base screen.
        rule.dumpScreenshot("grid-category-menu-open", rule.onAllNodes(isRoot()).onLast())
    }

    @Test
    fun grid_favouritesCategory() {
        val favPhotos = testPhotos.take(1)
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        state = GridUiState(
                            photos = favPhotos,
                            scope = CategoryScope.Category(Category.FAVOURITES_ID),
                            categories = categories,
                            memberships = mapOf(Category.FAVOURITES_ID to setOf(PhotoId("a"))),
                        ),
                        onBack = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("grid-favourites-category")
    }

    @Test
    fun grid_customCategoryActionsMenuOpen() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        state = GridUiState(
                            photos = testPhotos.take(1),
                            scope = CategoryScope.Category(selectsId),
                            categories = categories,
                            memberships = mapOf(selectsId to setOf(PhotoId("a"))),
                        ),
                        onBack = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Category actions").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Rename…").assertIsDisplayed()
        rule.onNodeWithText("Delete…").assertIsDisplayed()
        rule.dumpScreenshot("grid-custom-category-actions-menu", rule.onAllNodes(isRoot()).onLast())
    }

    @Test
    fun grid_focused() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        state = GridUiState(
                            photos = testPhotos,
                            scope = CategoryScope.AllPhotos,
                            categories = categories,
                            focusedIndex = 0,
                        ),
                        onBack = null,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("grid-focused")
    }

    @Test
    fun grid_empty() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(state = GridUiState(categories = categories), onBack = null)
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
