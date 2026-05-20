package com.vishalgupta.photoselector.data.format

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SkiaImageDecodingOrientationTest {

    private val tempFiles = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        tempFiles.forEach { it.deleteIfExists() }
    }

    @Test
    fun `orientation 1 keeps source dimensions`() {
        val path = writeJpeg(width = 40, height = 20)
        val decoded = SkiaImageDecoding.decode(path, targetMaxDimensionPx = null, orientation = 1)
        assertEquals(40, decoded.width)
        assertEquals(20, decoded.height)
    }

    @Test
    fun `orientations 5 through 8 swap width and height`() {
        val path = writeJpeg(width = 40, height = 20)
        for (orientation in 5..8) {
            val decoded = SkiaImageDecoding.decode(path, null, orientation)
            assertEquals(20, decoded.width, "orientation=$orientation width")
            assertEquals(40, decoded.height, "orientation=$orientation height")
        }
    }

    @Test
    fun `orientations 2 3 4 keep source dimensions`() {
        val path = writeJpeg(width = 40, height = 20)
        for (orientation in listOf(2, 3, 4)) {
            val decoded = SkiaImageDecoding.decode(path, null, orientation)
            assertEquals(40, decoded.width, "orientation=$orientation width")
            assertEquals(20, decoded.height, "orientation=$orientation height")
        }
    }

    @Test
    fun `targetMaxDimensionPx caps the post-rotation longest edge`() {
        val path = writeJpeg(width = 40, height = 20)
        // Source long edge is 40; after orientation 6 it becomes the height.
        val decoded = SkiaImageDecoding.decode(path, targetMaxDimensionPx = 20, orientation = 6)
        assertEquals(10, decoded.width)
        assertEquals(20, decoded.height)
    }

    @Test
    fun `rotate 180 swaps top and bottom rows`() {
        // 32x16 image: top half red, bottom half blue. Bigger blocks survive
        // JPEG's YCbCr chroma subsampling so we can sample sane centre pixels.
        val path = writeJpeg(width = 32, height = 16) { _, y ->
            if (y < 8) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        val decoded = SkiaImageDecoding.decode(path, null, orientation = 3)
        val top = sampleCentre(decoded, decoded.width / 2, 2)
        val bottom = sampleCentre(decoded, decoded.width / 2, decoded.height - 3)
        assertEquals(true, isMostlyBlue(top), "top row should be blue after 180; got ${top.toUInt().toString(16)}")
        assertEquals(true, isMostlyRed(bottom), "bottom row should be red after 180; got ${bottom.toUInt().toString(16)}")
    }

    @Test
    fun `rotate 90 CW maps left source column to top output row`() {
        // 32x16 image: left half red, right half blue.
        val path = writeJpeg(width = 32, height = 16) { x, _ ->
            if (x < 16) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        val decoded = SkiaImageDecoding.decode(path, null, orientation = 6)
        assertEquals(16, decoded.width)
        assertEquals(32, decoded.height)
        val top = sampleCentre(decoded, decoded.width / 2, 2)
        val bottom = sampleCentre(decoded, decoded.width / 2, decoded.height - 3)
        assertEquals(true, isMostlyRed(top), "top should be red after 90 CW; got ${top.toUInt().toString(16)}")
        assertEquals(true, isMostlyBlue(bottom), "bottom should be blue after 90 CW; got ${bottom.toUInt().toString(16)}")
    }

    private fun sampleCentre(decoded: com.vishalgupta.photoselector.domain.model.DecodedImage, x: Int, y: Int): Int {
        return decoded.argbPixels[y * decoded.width + x]
    }

    // --- helpers ---

    private fun isMostlyRed(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r > g + 40 && r > b + 40
    }

    private fun isMostlyBlue(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return b > g + 40 && b > r + 40
    }

    private fun writeJpeg(
        width: Int,
        height: Int,
        pixelArgb: (Int, Int) -> Int = { _, _ -> 0xFF808080.toInt() },
    ): Path {
        val info = ImageInfo(
            colorInfo = ColorInfo(
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.PREMUL,
                colorSpace = ColorSpace.sRGB,
            ),
            width = width,
            height = height,
        )
        val bitmap = Bitmap()
        try {
            bitmap.allocPixels(info)
            val bytes = ByteArray(width * height * 4)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val argb = pixelArgb(x, y)
                    val off = (y * width + x) * 4
                    bytes[off] = (argb and 0xFF).toByte()              // B
                    bytes[off + 1] = ((argb shr 8) and 0xFF).toByte()  // G
                    bytes[off + 2] = ((argb shr 16) and 0xFF).toByte() // R
                    bytes[off + 3] = ((argb shr 24) and 0xFF).toByte() // A
                }
            }
            bitmap.installPixels(bytes)
            val image = Image.makeFromBitmap(bitmap)
            try {
                val data = image.encodeToData(EncodedImageFormat.JPEG, 95)
                    ?: error("Could not encode test JPEG")
                try {
                    val file = Files.createTempFile("exif-decode-test", ".jpg")
                    tempFiles.add(file)
                    Files.write(file, data.bytes)
                    return file
                } finally {
                    data.close()
                }
            } finally {
                image.close()
            }
        } finally {
            bitmap.close()
        }
    }
}
