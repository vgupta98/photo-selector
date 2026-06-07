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
import com.vishalgupta.photoselector.data.format.HeicDecoder
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.assertTrue

/**
 * End-to-end check that a real HEIC decodes through [HeicDecoder]'s macOS ImageIO bridge and renders
 * correctly through the same Compose `Image` path the grid/browser use. The fixture is generated at
 * test time (Skia-encoded PNG -> `sips` -> HEIC), so nothing binary is checked in and `build/` stays
 * disposable. macOS-only (skiko can't decode HEIC and `sips` is a macOS tool).
 *
 * The image is top-half red, bottom-half blue. Rendering it back lets the screenshot+pixel asserts
 * catch the two pipeline bugs the unit test can't see: a BGRA<->RGBA channel swap (top would read
 * blue) and a vertical flip (top would read blue).
 */
class HeicDecodeScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private val tempFiles = mutableListOf<Path>()

    @After fun cleanup() {
        tempFiles.forEach { runCatching { it.deleteIfExists() } }
        tempFiles.clear()
    }

    @Test fun `heic decodes and renders with correct colors and orientation`() {
        assumeTrue("macOS-only HEIC decode", Platform.isMac())
        val heic = makeHeicFixture(width = 400, height = 300) ?: run {
            assumeTrue("sips HEIC conversion unavailable", false)
            return
        }

        val loader = SkikoImageLoader(
            registry = DefaultPhotoFormatRegistry(decoders = listOf(HeicDecoder())),
            decodeDispatcher = Dispatchers.IO,
        )
        val photo = Photo(
            id = PhotoId("heic"),
            absolutePath = heic,
            relativePath = heic.fileName.toString(),
            fileName = heic.fileName.toString(),
            sizeBytes = Files.size(heic),
            lastModifiedEpochMs = 0,
        )
        val bitmap = runBlocking { loader.load(photo, viewportLongEdgePx = 320) }
        requireNotNull(bitmap) { "loader returned null — HEIC decode failed" }

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
        rule.dumpScreenshot("heic-decode-color-orientation")

        // Top band is red, bottom band is blue. A channel swap or vertical flip flips these.
        val top = sampleAverageColor(captured, 0.40f, 0.60f, 0.15f, 0.30f)
        val bottom = sampleAverageColor(captured, 0.40f, 0.60f, 0.70f, 0.85f)
        assertTrue(top.red > 180 && top.blue < 80, "top band should read red, got $top")
        assertTrue(bottom.blue > 180 && bottom.red < 80, "bottom band should read blue, got $bottom")
    }

    /** Encodes a top-red / bottom-blue PNG via Skia, then converts it to HEIC with `sips`. */
    private fun makeHeicFixture(width: Int, height: Int): Path? {
        val info = ImageInfo(
            colorInfo = ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB),
            width = width,
            height = height,
        )
        val pixels = ByteArray(width * height * 4)
        for (y in 0 until height) {
            // BGRA little-endian: [B, G, R, A]
            val red = y < height / 2
            for (x in 0 until width) {
                val i = (y * width + x) * 4
                pixels[i] = if (red) 0 else 255.toByte()      // B
                pixels[i + 1] = 0                              // G
                pixels[i + 2] = if (red) 255.toByte() else 0  // R
                pixels[i + 3] = 255.toByte()                  // A
            }
        }
        val bitmap = Bitmap().apply {
            allocPixels(info)
            installPixels(info, pixels, info.minRowBytes)
        }
        val png = Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: return null
        bitmap.close()

        val pngPath = Files.createTempFile("heic-src-", ".png").also { tempFiles.add(it) }
        pngPath.writeBytes(png)
        val heicPath = Files.createTempFile("heic-fixture-", ".heic").also { tempFiles.add(it) }

        val exit = ProcessBuilder(
            "sips", "-s", "format", "heic",
            pngPath.toString(), "--out", heicPath.toString(),
        ).redirectErrorStream(true).start().waitFor()

        return if (exit == 0 && Files.size(heicPath) > 0) heicPath else null
    }

    private data class Sample(val red: Int, val green: Int, val blue: Int)

    private fun sampleAverageColor(
        image: ImageBitmap,
        fromXFraction: Float,
        toXFraction: Float,
        fromYFraction: Float,
        toYFraction: Float,
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
}
