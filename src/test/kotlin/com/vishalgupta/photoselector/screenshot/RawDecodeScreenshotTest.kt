package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.sun.jna.Platform
import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.RawDecoder
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * End-to-end render check: a real camera RAW decodes through [RawDecoder]'s macOS ImageIO bridge and
 * renders through the same Compose `Image` path the grid/browser use, exercising the BGRA->Compose
 * conversion on real RAW pixels. The colour/orientation correctness of that conversion is pinned by
 * [HeicDecodeScreenshotTest] (which uses a synthesised known-colour image); RAW can't be synthesised,
 * so this self-skips unless real samples are dropped in `build/raw-probe/`, and when present it dumps
 * one PNG per brand for eyeballing plus a blank-frame guard (a real photo is never one flat colour).
 */
class RawDecodeScreenshotTest {

    @get:Rule val rule = createComposeRule()

    @Test fun `each real RAW decodes and renders a non-blank frame`() {
        assumeTrue("macOS-only RAW decode", Platform.isMac())
        val fixtures = locateFixtures()
        assumeTrue("drop sample RAW files in build/raw-probe/ to exercise this", fixtures.isNotEmpty())

        val loader = SkikoImageLoader(
            registry = DefaultPhotoFormatRegistry(decoders = listOf(RawDecoder())),
            decodeDispatcher = Dispatchers.IO,
        )

        fixtures.forEach { raw ->
            val photo = Photo(
                id = PhotoId(raw.fileName.toString()),
                absolutePath = raw,
                relativePath = raw.fileName.toString(),
                fileName = raw.fileName.toString(),
                sizeBytes = Files.size(raw),
                lastModifiedEpochMs = 0,
            )
            val bitmap = runBlocking { loader.load(photo, viewportLongEdgePx = 512) }
            requireNotNull(bitmap) { "loader returned null — RAW decode failed for ${raw.fileName}" }

            rule.setContent {
                AppTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                        )
                    }
                }
            }
            rule.waitForIdle()

            val captured = rule.onRoot().captureToImage()
            rule.dumpScreenshot("raw-decode-${raw.fileName.toString().replace('.', '-')}")
            assertTrue(hasColourVariance(captured), "rendered RAW ${raw.fileName} should not be one flat colour")
        }
    }

    /** True if the frame contains meaningfully different pixels (a decoded photo always does). */
    private fun hasColourVariance(image: ImageBitmap): Boolean {
        val pixels = image.toPixelMap()
        val stepX = (image.width / 16).coerceAtLeast(1)
        val stepY = (image.height / 16).coerceAtLeast(1)
        var minL = Int.MAX_VALUE
        var maxL = Int.MIN_VALUE
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val c = pixels[x, y]
                val lum = ((c.red + c.green + c.blue) * 255 / 3).toInt()
                if (lum < minL) minL = lum
                if (lum > maxL) maxL = lum
                x += stepX
            }
            y += stepY
        }
        return maxL - minL > 16
    }

    /** Every file under build/raw-probe/ whose extension RawDecoder claims. */
    private fun locateFixtures(): List<Path> {
        val dir = Path.of("build/raw-probe")
        if (!Files.isDirectory(dir)) return emptyList()
        val exts = RawDecoder().format.extensions
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in exts }
                .sorted()
                .toList()
        }
    }
}
