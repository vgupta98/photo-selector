package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

/**
 * Guards that the modifier-aware tile click still routes a plain (unmodified) click to onClick —
 * i.e. reading the window modifiers at click time didn't break the proven open path. The Cmd /
 * Shift branches read live window keyboard-modifier state, which the headless harness can't put
 * into a "Meta held" state for a semantics click, so those are verified manually (`./gradlew run`)
 * per the live-window carve-out.
 */
class PhotoThumbnailClickTest {

    @get:Rule
    val rule = createComposeRule()

    private val photo = Photo(
        id = PhotoId("a"),
        absolutePath = Path.of("/photos/sunset.jpg"),
        relativePath = "sunset.jpg",
        fileName = "sunset.jpg",
        sizeBytes = 1,
        lastModifiedEpochMs = 0,
    )

    private val noOpImageLoader = object : ImageLoader {
        override suspend fun load(photo: Photo, viewportLongEdgePx: Int): ImageBitmap? = null
        override fun prefetch(photos: List<Photo>, viewportLongEdgePx: Int, scope: CoroutineScope) {}
        override fun evictAll() {}
        override fun pin(id: PhotoId) {}
        override fun unpinAllExcept(id: PhotoId?) {}
    }

    @androidx.compose.runtime.Composable
    private fun Tile(onClick: () -> Unit, onToggleSelect: () -> Unit) {
        Surface(Modifier.size(200.dp)) {
            PhotoThumbnail(
                photo = photo,
                loader = noOpImageLoader,
                isMarked = false,
                isFocused = false,
                onClick = onClick,
                onToggleSelect = onToggleSelect,
                onRangeSelect = {},
                modifier = Modifier.size(200.dp).testTag("tile"),
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plainClick_opens_notSelect() {
        var opens = 0
        var toggles = 0
        rule.setContent { AppTheme { Tile(onClick = { opens++ }, onToggleSelect = { toggles++ }) } }
        rule.waitForIdle()

        rule.onNodeWithTag("tile").performClick()
        rule.waitForIdle()

        assertEquals(1, opens)
        assertEquals(0, toggles)
    }
}
