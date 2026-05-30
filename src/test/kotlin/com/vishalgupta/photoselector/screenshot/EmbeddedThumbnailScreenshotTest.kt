package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.ExifFixtureBuilder
import com.vishalgupta.photoselector.data.format.JpegDecoder
import com.vishalgupta.photoselector.data.format.encodeHorizontalBandJpeg
import com.vishalgupta.photoselector.data.format.encodeSolidJpeg
import com.vishalgupta.photoselector.data.format.spliceApp1IntoJpeg
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color as SkColor
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check: load a JPEG with an embedded EXIF thumbnail through the real
 * [SkikoImageLoader] + [JpegDecoder] fast path, render it through the same Compose
 * `Image` composable the favourites grid uses, dump a PNG to `build/screenshots/`,
 * and pixel-assert the rotated color bands land where orientation 6 says they should.
 *
 * Catches breakage the unit pixel asserts can't: incorrect ImageBitmap wiring, broken
 * Compose theming, or a `ContentScale.Crop` interaction that flips/clips the result.
 */
class EmbeddedThumbnailScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val tempFiles = mutableListOf<Path>()

    @After fun cleanup() {
        tempFiles.forEach { runCatching { it.deleteExisting() } }
        tempFiles.clear()
    }

    @Test fun `embedded thumb with orientation 6 renders rotated in the favourites tile`() {
        // 240x240 thumb: top half red, bottom half blue. With orientation 6 (90 CW)
        // the rendered tile should have the BLUE band on the LEFT and RED on the RIGHT.
        val outer = encodeSolidJpeg(800, 800, SkColor.makeARGB(255, 0, 255, 0))
        val thumb = encodeHorizontalBandJpeg(
            width = 240,
            height = 240,
            topArgb = SkColor.makeARGB(255, 255, 0, 0),
            bottomArgb = SkColor.makeARGB(255, 0, 0, 255),
        )
        val file = writeTemp(
            spliceApp1IntoJpeg(
                outer,
                ExifFixtureBuilder().orientation(6).thumbnail(thumb).buildApp1Body(),
            ),
        )

        val loader = SkikoImageLoader(
            registry = DefaultPhotoFormatRegistry(decoders = listOf(JpegDecoder())),
            decodeDispatcher = Dispatchers.IO,
        )
        val photo = Photo(
            id = PhotoId("orient-6"),
            absolutePath = file,
            relativePath = file.fileName.toString(),
            fileName = file.fileName.toString(),
            sizeBytes = Files.size(file),
            lastModifiedEpochMs = 0,
        )
        val bitmap = runBlocking { loader.load(photo, viewportLongEdgePx = 320) }
        requireNotNull(bitmap) { "loader returned null — fast path failed" }

        rule.setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    )
                }
            }
        }
        rule.waitForIdle()

        val captured = rule.onRoot().captureToImage()
        rule.dumpScreenshot("embedded-thumb-orientation-6")

        // Sample the left and right thirds of the rendered tile at mid-height. After 90 CW
        // rotation of (top=red, bottom=blue), left should be blue and right should be red.
        val left = sampleAverageColor(captured, fromXFraction = 0.10f, toXFraction = 0.30f)
        val right = sampleAverageColor(captured, fromXFraction = 0.70f, toXFraction = 0.90f)
        assertChannel("left blue R", expected = 0, actual = left.red)
        assertChannel("left blue G", expected = 0, actual = left.green)
        assertChannel("left blue B", expected = 255, actual = left.blue)
        assertChannel("right red R", expected = 255, actual = right.red)
        assertChannel("right red G", expected = 0, actual = right.green)
        assertChannel("right red B", expected = 0, actual = right.blue)
    }

    @Test fun `side-by-side baseline shows fast-path and full-decode tiles render the same color`() {
        // Sanity: a JPEG without an embedded thumb (forces full decode) and one with an
        // embedded thumb (forces fast path), both solid red, render visually identical
        // inside a tile. Mostly a screenshot-eyeball test; the assertion just confirms
        // both halves are red.
        val outerRed = encodeSolidJpeg(800, 800, SkColor.makeARGB(255, 220, 30, 30))
        val plainFile = writeTemp(outerRed)
        val withThumb = writeTemp(
            spliceApp1IntoJpeg(
                outerRed,
                ExifFixtureBuilder()
                    .thumbnail(encodeSolidJpeg(240, 240, SkColor.makeARGB(255, 220, 30, 30)))
                    .buildApp1Body(),
            ),
        )

        val loader = SkikoImageLoader(
            registry = DefaultPhotoFormatRegistry(decoders = listOf(JpegDecoder())),
            decodeDispatcher = Dispatchers.IO,
        )
        val left = runBlocking {
            loader.load(plainFile.asPhoto("plain"), viewportLongEdgePx = 320)
        }
        val right = runBlocking {
            loader.load(withThumb.asPhoto("with-thumb"), viewportLongEdgePx = 320)
        }
        requireNotNull(left); requireNotNull(right)

        rule.setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = left,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        Image(
                            bitmap = right,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        val captured = rule.onRoot().captureToImage()
        rule.dumpScreenshot("embedded-thumb-vs-full-decode")

        val leftSample = sampleAverageColor(captured, 0.10f, 0.40f)
        val rightSample = sampleAverageColor(captured, 0.60f, 0.90f)
        assertTrue(leftSample.red > 180, "left tile should be predominantly red, got $leftSample")
        assertTrue(rightSample.red > 180, "right tile should be predominantly red, got $rightSample")
    }

    private fun Path.asPhoto(id: String): Photo = Photo(
        id = PhotoId(id),
        absolutePath = this,
        relativePath = fileName.toString(),
        fileName = fileName.toString(),
        sizeBytes = Files.size(this),
        lastModifiedEpochMs = 0,
    )

    private fun writeTemp(bytes: ByteArray): Path {
        val p = Files.createTempFile("screenshot-test-", ".jpg")
        p.writeBytes(bytes)
        tempFiles.add(p)
        return p
    }

    private data class Sample(val red: Int, val green: Int, val blue: Int)

    private fun sampleAverageColor(
        image: ImageBitmap,
        fromXFraction: Float,
        toXFraction: Float,
        fromYFraction: Float = 0.40f,
        toYFraction: Float = 0.60f,
    ): Sample {
        val pixels = image.toPixelMap()
        val fromX = (image.width * fromXFraction).toInt().coerceIn(0, image.width - 1)
        val toX = (image.width * toXFraction).toInt().coerceIn(fromX + 1, image.width)
        val fromY = (image.height * fromYFraction).toInt().coerceIn(0, image.height - 1)
        val toY = (image.height * toYFraction).toInt().coerceIn(fromY + 1, image.height)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in fromY until toY) {
            for (x in fromX until toX) {
                val c = pixels[x, y]
                r += (c.red * 255).toInt()
                g += (c.green * 255).toInt()
                b += (c.blue * 255).toInt()
                n++
            }
        }
        return Sample((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun assertChannel(label: String, expected: Int, actual: Int, tolerance: Int = 25) {
        assertEquals(
            true,
            abs(expected - actual) <= tolerance,
            "$label: got $actual, expected within $tolerance of $expected",
        )
    }
}
