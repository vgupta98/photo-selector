package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.common.GroupingMode
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.grid.GridScreen
import com.vishalgupta.photoselector.presentation.grid.GridUiState
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

/**
 * Renders the redesigned grid shell — the left [com.vishalgupta.photoselector.presentation.designsystem.organism.LibraryRail]
 * beside the slimmed top bar — through the real [GridScreen]. Eyeball the PNGs under
 * `build/screenshots/`:
 *  - `library-rail-all-photos`: rail expanded, "All Photos" active, Favourites + two custom
 *    categories with counts, the top bar carrying only identity + the grouping toggle (no Export
 *    in All Photos), and the far-left collapse toggle.
 *  - `library-rail-category`: a custom category active (highlighted row), with the top bar's
 *    consolidated **Export** menu showing for the exportable scope.
 *  - `library-rail-collapsed`: rail hidden, grid full-bleed, the top bar showing the "Show sidebar"
 *    toggle on the far left.
 */
class LibraryRailScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val photos = listOf("a", "b", "c", "d", "e", "f").map { id ->
        Photo(
            id = PhotoId(id),
            absolutePath = Path.of("/photos/$id.jpg"),
            relativePath = "$id.jpg",
            fileName = "$id.jpg",
            sizeBytes = 1024,
            lastModifiedEpochMs = 0,
        )
    }

    private val keepers = Category(CategoryId("keepers"), "Keepers", builtIn = false)
    private val portfolio = Category(CategoryId("portfolio"), "Portfolio", builtIn = false)
    private val categories = listOf(Category.favourites(), keepers, portfolio)

    private val memberships = mapOf(
        Category.FAVOURITES_ID to setOf(photos[0].id, photos[2].id),
        keepers.id to setOf(photos[0].id, photos[1].id, photos[3].id),
        portfolio.id to setOf(photos[4].id),
    )

    private val palette = mapOf(
        "a" to Color(0xFF6FCF97), "b" to Color(0xFF56CCF2), "c" to Color(0xFF2F80ED),
        "d" to Color(0xFF9B51E0), "e" to Color(0xFFF2994A), "f" to Color(0xFFEB5757),
    )

    private val colorLoader = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap =
            solid(palette[photo.id.value] ?: Color.Gray)

        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    @Test fun `rail expanded over all photos`() {
        renderShell(
            GridUiState(
                photos = photos,
                groups = photos.map(com.vishalgupta.photoselector.domain.model.PhotoGroup::Single),
                groupingMode = GroupingMode.Off,
                scope = CategoryScope.AllPhotos,
                categories = categories,
                memberships = memberships,
            ),
        )
        rule.dumpScreenshot("library-rail-all-photos")
    }

    @Test fun `rail highlights the active category and the bar shows export`() {
        renderShell(
            GridUiState(
                // A category scope shows just its members; the rail still lists the whole library.
                photos = listOf(photos[0], photos[1], photos[3]),
                groups = listOf(photos[0], photos[1], photos[3]).map(com.vishalgupta.photoselector.domain.model.PhotoGroup::Single),
                groupingMode = GroupingMode.Off,
                scope = CategoryScope.Category(keepers.id),
                categories = categories,
                memberships = memberships,
            ),
        )
        rule.dumpScreenshot("library-rail-category")
    }

    @Test fun `rail collapsed leaves the grid full-bleed`() {
        renderShell(
            GridUiState(
                photos = photos,
                groups = photos.map(com.vishalgupta.photoselector.domain.model.PhotoGroup::Single),
                groupingMode = GroupingMode.Off,
                scope = CategoryScope.AllPhotos,
                categories = categories,
                memberships = memberships,
            ),
            railCollapsed = true,
        )
        rule.dumpScreenshot("library-rail-collapsed")
    }

    private fun renderShell(state: GridUiState, railCollapsed: Boolean = false) {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(1100.dp, 560.dp)) {
                    GridScreen(
                        state = state,
                        initialScrollIndex = 0,
                        rootName = "Iceland 2026",
                        railCollapsed = railCollapsed,
                        onToggleRail = {},
                        onTileClick = {},
                        onChangeFolder = {},
                        onSelectAllPhotos = {},
                        onSelectCategory = { _, _ -> },
                        onCreateCategory = {},
                        onRenameCategory = { _, _ -> },
                        onDeleteCategory = {},
                        onBack = if (state.scope is CategoryScope.Category) ({}) else null,
                        onSetFocusedIndex = {},
                        onToggleMembershipAtFocus = {},
                        onToggleCustomCategoryAtFocus = {},
                        onExportTxt = {},
                        onCopyToFolder = {},
                        onDismissToast = {},
                        imageLoader = colorLoader,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun solid(color: Color): ImageBitmap {
        val bmp = ImageBitmap(96, 96)
        Canvas(bmp).drawRect(0f, 0f, 96f, 96f, Paint().apply { this.color = color })
        return bmp
    }
}
