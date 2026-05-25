package com.vishalgupta.photoselector.data.format

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import org.jetbrains.skia.Matrix33
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeBytes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JpegDecoderTest {

    private val tempFiles = mutableListOf<Path>()
    private val decoder = JpegDecoder()

    @org.junit.After fun cleanup() {
        tempFiles.forEach { runCatching { it.deleteExisting() } }
        tempFiles.clear()
    }

    // --- Matrix correctness ---------------------------------------------------------------

    @Test fun `orientation matrix maps source corners to expected canvas corners`() {
        val srcW = 4f; val srcH = 2f; val scale = 1f
        // For each orientation, the 4 source corners (TL, TR, BR, BL) should land at these
        // canvas points. Derivation: see the orientation table in JpegDecoder.kt.
        val expectations = mapOf(
            1 to listOf(0f to 0f, srcW to 0f, srcW to srcH, 0f to srcH),
            2 to listOf(srcW to 0f, 0f to 0f, 0f to srcH, srcW to srcH),
            3 to listOf(srcW to srcH, 0f to srcH, 0f to 0f, srcW to 0f),
            4 to listOf(0f to srcH, srcW to srcH, srcW to 0f, 0f to 0f),
            // Orientations 5..8 swap dimensions: outW=srcH, outH=srcW.
            5 to listOf(0f to 0f, 0f to srcW, srcH to srcW, srcH to 0f),
            6 to listOf(srcH to 0f, srcH to srcW, 0f to srcW, 0f to 0f),
            7 to listOf(srcH to srcW, srcH to 0f, 0f to 0f, 0f to srcW),
            8 to listOf(0f to srcW, 0f to 0f, srcH to 0f, srcH to srcW),
        )
        val corners = listOf(0f to 0f, srcW to 0f, srcW to srcH, 0f to srcH)
        for ((o, expected) in expectations) {
            val swap = o in 5..8
            val outW = if (swap) srcH else srcW
            val outH = if (swap) srcW else srcH
            val m = JpegDecoder.orientationMatrix(o, scale, outW, outH)
            corners.forEachIndexed { i, (x, y) ->
                val (nx, ny) = m.apply(x, y)
                assertEquals(expected[i].first, nx, "orientation $o corner $i x")
                assertEquals(expected[i].second, ny, "orientation $o corner $i y")
            }
        }
    }

    private fun Matrix33.apply(x: Float, y: Float): Pair<Float, Float> {
        val a = mat
        return (a[0] * x + a[1] * y + a[2]) to (a[3] * x + a[4] * y + a[5])
    }

    // --- End-to-end fast path -------------------------------------------------------------

    @Test fun `small target uses embedded thumb when present`() {
        // Outer is solid blue (visible only via the slow path), thumb is solid red.
        val outer = encodeSolidJpeg(600, 600, Color.makeARGB(255, 0, 0, 255))
        val thumb = encodeSolidJpeg(240, 240, Color.makeARGB(255, 255, 0, 0))
        val path = writeTemp(spliceApp1IntoJpeg(outer, ExifFixtureBuilder().thumbnail(thumb).buildApp1Body()))

        val decoded = runBlocking { decoder.decode(path, targetMaxDimensionPx = 320) }

        // Thumb is 240; 240 <= 320 so scale is 1 → output is 240x240.
        assertEquals(240, decoded.width)
        assertEquals(240, decoded.height)
        assertChannelCloseTo(red = 255, green = 0, blue = 0, decoded = decoded)
    }

    @Test fun `target above threshold bypasses fast path`() {
        val outer = encodeSolidJpeg(800, 800, Color.makeARGB(255, 0, 0, 255))
        val thumb = encodeSolidJpeg(240, 240, Color.makeARGB(255, 255, 0, 0))
        val path = writeTemp(spliceApp1IntoJpeg(outer, ExifFixtureBuilder().thumbnail(thumb).buildApp1Body()))

        // 1600 > 384 threshold → slow path runs and decodes the outer.
        val decoded = runBlocking { decoder.decode(path, targetMaxDimensionPx = 1600) }

        assertEquals(800, decoded.width)
        assertEquals(800, decoded.height)
        assertChannelCloseTo(red = 0, green = 0, blue = 255, decoded = decoded)
    }

    @Test fun `no embedded thumb falls back to full decode`() {
        val outer = encodeSolidJpeg(600, 600, Color.makeARGB(255, 0, 255, 0))
        val path = writeTemp(outer)

        val decoded = runBlocking { decoder.decode(path, targetMaxDimensionPx = 320) }

        // Slow path scales 600 → 320.
        assertEquals(320, decoded.width)
        assertChannelCloseTo(red = 0, green = 255, blue = 0, decoded = decoded)
    }

    @Test fun `tiny thumbnail rejects fast path to avoid upscaling`() {
        // Thumb is 100 px; with target 320, longest (100) < target/2 (160) → fall back.
        val outer = encodeSolidJpeg(600, 600, Color.makeARGB(255, 0, 0, 255))
        val thumb = encodeSolidJpeg(100, 100, Color.makeARGB(255, 255, 0, 0))
        val path = writeTemp(spliceApp1IntoJpeg(outer, ExifFixtureBuilder().thumbnail(thumb).buildApp1Body()))

        val decoded = runBlocking { decoder.decode(path, targetMaxDimensionPx = 320) }

        // Should have gone through the full decode → output is 320 (outer scaled), blue.
        assertEquals(320, decoded.width)
        assertChannelCloseTo(red = 0, green = 0, blue = 255, decoded = decoded)
    }

    @Test fun `embedded thumb with orientation 6 produces swapped dimensions`() {
        // 200x120 thumb with orientation 6 (90 CW) should produce 120x200 output.
        val outer = encodeSolidJpeg(800, 800, Color.makeARGB(255, 0, 0, 255))
        val thumb = encodeSolidJpeg(200, 120, Color.makeARGB(255, 255, 0, 0))
        val path = writeTemp(
            spliceApp1IntoJpeg(
                outer,
                ExifFixtureBuilder().orientation(6).thumbnail(thumb).buildApp1Body(),
            ),
        )

        val decoded = runBlocking { decoder.decode(path, targetMaxDimensionPx = 320) }

        // Post-orientation dims: 120 x 200. Longest (200) ≤ 320, so scale is 1.
        assertEquals(120, decoded.width)
        assertEquals(200, decoded.height)
        assertChannelCloseTo(red = 255, green = 0, blue = 0, decoded = decoded)
    }

    // --- helpers --------------------------------------------------------------------------

    private fun writeTemp(bytes: ByteArray): Path {
        val p = Files.createTempFile("jpegdecoder-test-", ".jpg")
        p.writeBytes(bytes)
        tempFiles.add(p)
        return p
    }

    /** Tolerant per-channel comparison: JPEG quantization shifts solid colors by a few values. */
    private fun assertChannelCloseTo(
        red: Int,
        green: Int,
        blue: Int,
        decoded: com.vishalgupta.photoselector.domain.model.DecodedImage,
        pixelIndex: Int = 0,
        tolerance: Int = 12,
    ) {
        val base = pixelIndex * 4
        val b = decoded.bgraBytes[base].toInt() and 0xFF
        val g = decoded.bgraBytes[base + 1].toInt() and 0xFF
        val r = decoded.bgraBytes[base + 2].toInt() and 0xFF
        assertTrue(abs(r - red) <= tolerance, "red: got $r, expected near $red")
        assertTrue(abs(g - green) <= tolerance, "green: got $g, expected near $green")
        assertTrue(abs(b - blue) <= tolerance, "blue: got $b, expected near $blue")
    }
}
