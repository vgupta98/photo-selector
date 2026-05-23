package com.vishalgupta.photoselector.perf

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JPEG fixtures for the JMH benchmarks. Duplicates a trimmed-down version of
 * `src/test/kotlin/.../TestExifFixtures.kt` because JMH lives in its own
 * source set and the test helpers are `internal`. Keep this file minimal —
 * the screenshot tests are the canonical encoder.
 */
internal object JpegFixture {

    /**
     * Encodes a two-band JPEG at [width] × [height]. Two solid bands compress
     * to ~1–3% of the raw pixel count, which is enough entropy work to make
     * the decode path representative without producing wedding-photo-sized
     * files (~5 MB) inside the JMH trial setup.
     */
    fun bandJpeg(width: Int, height: Int, quality: Int = 90): ByteArray {
        val info = ImageInfo(
            colorInfo = ColorInfo(ColorType.BGRA_8888, ColorAlphaType.OPAQUE, ColorSpace.sRGB),
            width = width,
            height = height,
        )
        val surface = Surface.makeRaster(info)
        try {
            val paint = Paint()
            paint.color = 0xFFFF2030.toInt()
            surface.canvas.drawRect(Rect.makeXYWH(0f, 0f, width.toFloat(), height / 2f), paint)
            paint.color = 0xFF2090FF.toInt()
            surface.canvas.drawRect(Rect.makeXYWH(0f, height / 2f, width.toFloat(), height / 2f), paint)
            val image = surface.makeImageSnapshot()
            try {
                return image.encodeToData(EncodedImageFormat.JPEG, quality)!!.bytes
            } finally {
                image.close()
            }
        } finally {
            surface.close()
        }
    }

    /** APP1 body: "Exif\0\0" + a TIFF with IFD0 (Orientation = 1) + IFD1 (embedded JPEG thumb). */
    fun app1WithEmbeddedThumb(thumb: ByteArray): ByteArray =
        "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) + buildTiff(thumb)

    /** Inserts an APP1 segment right after the SOI of [outerJpeg]. */
    fun spliceApp1(outerJpeg: ByteArray, app1Body: ByteArray): ByteArray {
        require(outerJpeg.size >= 2 && outerJpeg[0] == 0xFF.toByte() && outerJpeg[1] == 0xD8.toByte()) {
            "outerJpeg must start with SOI"
        }
        val app1Length = app1Body.size + 2
        require(app1Length <= 0xFFFF)
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putShort(0xFFE1.toShort())
            .putShort(app1Length.toShort())
            .array()
        return outerJpeg.copyOfRange(0, 2) + header + app1Body + outerJpeg.copyOfRange(2, outerJpeg.size)
    }

    private fun buildTiff(thumb: ByteArray): ByteArray {
        val ifd0Entries = 1
        val ifd0Size = 2 + ifd0Entries * 12 + 4
        val ifd1Entries = 2
        val ifd1Size = 2 + ifd1Entries * 12 + 4
        val ifd0Offset = 8
        val ifd1Offset = ifd0Offset + ifd0Size
        val thumbOffset = ifd1Offset + ifd1Size

        val total = 8 + ifd0Size + ifd1Size + thumb.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)

        buf.put(0x49); buf.put(0x49)
        buf.putShort(42)
        buf.putInt(ifd0Offset)

        buf.putShort(ifd0Entries.toShort())
        buf.putShort(0x0112.toShort())
        buf.putShort(3)
        buf.putInt(1)
        buf.putShort(1)
        buf.putShort(0)
        buf.putInt(ifd1Offset)

        buf.putShort(ifd1Entries.toShort())
        buf.putShort(0x0201.toShort())
        buf.putShort(4)
        buf.putInt(1)
        buf.putInt(thumbOffset)
        buf.putShort(0x0202.toShort())
        buf.putShort(4)
        buf.putInt(1)
        buf.putInt(thumb.size)
        buf.putInt(0)
        buf.put(thumb)
        return buf.array()
    }
}
