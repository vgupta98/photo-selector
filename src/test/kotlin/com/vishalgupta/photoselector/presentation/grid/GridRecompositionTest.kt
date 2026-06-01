package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.RecompositionTracker
import com.vishalgupta.photoselector.presentation.designsystem.organism.PhotoThumbnail
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

/**
 * Guards the one genuinely hot recomposition path: the thumbnail tiles. The grid
 * recomputes a per-tile `isFavourite`/`isFocused` on every state change, so when
 * one tile's favourite or focus flips, every tile's item block re-runs — but
 * strong skipping must keep [PhotoThumbnail] from actually recomposing for the
 * tiles whose inputs didn't change.
 *
 * [CountedThumbnail] forwards the exact params [PhotoThumbnail] receives and
 * records in its own body, so it skips / recomposes under the same stability
 * rules the real tile does. A regression that makes tiles churn — an unstable
 * param sneaking into PhotoThumbnail, a holder losing @Immutable — flips a
 * "should skip" assertion here instead of silently costing frames in the grid.
 *
 * The harness uses a plain [FlowRow], not a LazyVerticalGrid: that isolates
 * component-level skippability from the lazy grid's own item-subcomposition
 * machinery (library-owned, recomposes items on its own schedule). The per-tile
 * inputs are computed exactly as GridScreen computes them.
 */
class GridRecompositionTest {

    @get:Rule
    val rule = createComposeRule()

    private val photos = listOf("a", "b", "c", "d").map { id ->
        Photo(
            id = PhotoId(id),
            absolutePath = Path.of("/photos/$id.jpg"),
            relativePath = "$id.jpg",
            fileName = "$id.jpg",
            sizeBytes = 1024,
            lastModifiedEpochMs = 0,
        )
    }

    private val noOpImageLoader = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? = null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    @Test
    fun togglingOneFavourite_recomposesOnlyThatTile() {
        val tracker = RecompositionTracker()
        val favourites = mutableStateOf(emptySet<PhotoId>())

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        photos = photos,
                        favourites = favourites.value,
                        focusedIndex = -1,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { favourites.value = setOf(PhotoId("b")) }
        rule.waitForIdle()

        assertEquals("b should recompose (its favourite flipped)", before["b"]!! + 1, tracker["b"])
        assertEquals("a should skip", before["a"], tracker["a"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }

    @Test
    fun movingFocus_recomposesOnlyTheTilesGainingAndLosingFocus() {
        val tracker = RecompositionTracker()
        val focusedIndex = mutableStateOf(0)

        rule.setContent {
            AppTheme {
                Surface(Modifier.size(800.dp, 600.dp)) {
                    Grid(
                        photos = photos,
                        favourites = emptySet(),
                        focusedIndex = focusedIndex.value,
                        loader = noOpImageLoader,
                        tracker = tracker,
                    )
                }
            }
        }
        rule.waitForIdle()

        val before = photos.associate { it.id.value to tracker[it.id.value] }

        rule.runOnIdle { focusedIndex.value = 1 } // move focus a -> b
        rule.waitForIdle()

        assertEquals("a should recompose (lost focus)", before["a"]!! + 1, tracker["a"])
        assertEquals("b should recompose (gained focus)", before["b"]!! + 1, tracker["b"])
        assertEquals("c should skip", before["c"], tracker["c"])
        assertEquals("d should skip", before["d"], tracker["d"])
    }
}

/** One tile per photo, with per-tile inputs computed as in GridScreen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Grid(
    photos: List<Photo>,
    favourites: Set<PhotoId>,
    focusedIndex: Int,
    loader: ImageLoader,
    tracker: RecompositionTracker,
) {
    FlowRow {
        photos.forEachIndexed { index, photo ->
            CountedThumbnail(
                tracker = tracker,
                tag = photo.id.value,
                photo = photo,
                loader = loader,
                isFavourite = photo.id in favourites,
                isFocused = index == focusedIndex,
            )
        }
    }
}

/**
 * Records one recomposition then renders the real [PhotoThumbnail] with the same
 * params. [record] sits in this scope's body (not behind a content lambda), so it
 * ticks exactly when this skippable composable recomposes — i.e. when one of the
 * forwarded params actually changed.
 */
@Composable
private fun CountedThumbnail(
    tracker: RecompositionTracker,
    tag: String,
    photo: Photo,
    loader: ImageLoader,
    isFavourite: Boolean,
    isFocused: Boolean,
) {
    tracker.record(tag)
    PhotoThumbnail(
        photo = photo,
        loader = loader,
        isMarked = isFavourite,
        isFocused = isFocused,
        onClick = {},
        modifier = Modifier.size(AppTheme.dimens.thumbnailMinCell),
    )
}
