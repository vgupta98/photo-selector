package com.vishalgupta.photoselector.data.format

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
 * Builder for a minimal SOI + APP1(Exif) JPEG used by [ExifReaderTest] and
 * [JpegDecoderTest]. Lays out IFD0 right after the TIFF header, IFD1 right
 * after IFD0, and the thumbnail right after IFD1.
 */
internal class ExifFixtureBuilder(private val littleEndian: Boolean = true) {
    private var orientation: Int? = null
    private var thumb: ByteArray? = null

    fun orientation(o: Int) = apply { orientation = o }
    fun thumbnail(bytes: ByteArray) = apply { thumb = bytes }

    /** Returns SOI + APP1(Exif) only — no scan data. Decodable by [ExifReader] but not by Skia. */
    fun buildJpeg(): ByteArray {
        val app1Body = buildApp1Body()
        val app1Length = app1Body.size + 2
        require(app1Length <= 0xFFFF)
        return ByteBuffer.allocate(2 + 2 + app1Length).order(ByteOrder.BIG_ENDIAN)
            .putShort(0xFFD8.toShort())
            .putShort(0xFFE1.toShort())
            .putShort(app1Length.toShort())
            .put(app1Body)
            .array()
    }

    /** Returns just the APP1 body ("Exif\0\0" + TIFF). Suitable for splicing into another JPEG. */
    fun buildApp1Body(): ByteArray =
        "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) + buildTiff()

    private fun buildTiff(): ByteArray {
        val ifd0Entries = if (orientation != null) 1 else 0
        val ifd0Size = 2 + ifd0Entries * 12 + 4

        val ifd1Entries = if (thumb != null) 2 else 0
        val ifd1Size = 2 + ifd1Entries * 12 + 4

        val ifd0Offset = 8
        val ifd1Offset = ifd0Offset + ifd0Size
        val thumbOffset = ifd1Offset + ifd1Size
        val thumbSize = thumb?.size ?: 0

        val totalSize = 8 + ifd0Size + ifd1Size + thumbSize
        val buf = ByteBuffer.allocate(totalSize)
            .order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        if (littleEndian) { buf.put(0x49); buf.put(0x49) } else { buf.put(0x4D); buf.put(0x4D) }
        buf.putShort(42)
        buf.putInt(ifd0Offset)

        buf.putShort(ifd0Entries.toShort())
        orientation?.let { o ->
            buf.putShort(0x0112.toShort())
            buf.putShort(3) // SHORT
            buf.putInt(1)
            buf.putShort(o.toShort())
            buf.putShort(0)
        }
        buf.putInt(if (thumb != null) ifd1Offset else 0)

        if (thumb != null) {
            buf.putShort(ifd1Entries.toShort())
            buf.putShort(0x0201.toShort()) // JPEGInterchangeFormat
            buf.putShort(4) // LONG
            buf.putInt(1)
            buf.putInt(thumbOffset)
            buf.putShort(0x0202.toShort()) // JPEGInterchangeFormatLength
            buf.putShort(4)
            buf.putInt(1)
            buf.putInt(thumbSize)
            buf.putInt(0)
            buf.put(thumb)
        }
        return buf.array()
    }
}

/** Encodes a solid-color JPEG with the given ARGB color. */
internal fun encodeSolidJpeg(width: Int, height: Int, argb: Int, quality: Int = 90): ByteArray =
    encodeJpeg(width, height, quality) { canvas, paint ->
        paint.color = argb
        canvas.drawRect(Rect.makeXYWH(0f, 0f, width.toFloat(), height.toFloat()), paint)
    }

/**
 * Encodes a JPEG split into two horizontal bands: the top half is [topArgb],
 * the bottom half is [bottomArgb]. Useful for verifying orientation visually.
 */
internal fun encodeHorizontalBandJpeg(
    width: Int,
    height: Int,
    topArgb: Int,
    bottomArgb: Int,
    quality: Int = 90,
): ByteArray = encodeJpeg(width, height, quality) { canvas, paint ->
    paint.color = topArgb
    canvas.drawRect(Rect.makeXYWH(0f, 0f, width.toFloat(), height / 2f), paint)
    paint.color = bottomArgb
    canvas.drawRect(Rect.makeXYWH(0f, height / 2f, width.toFloat(), height / 2f), paint)
}

private inline fun encodeJpeg(
    width: Int,
    height: Int,
    quality: Int,
    drawWith: (org.jetbrains.skia.Canvas, Paint) -> Unit,
): ByteArray {
    val info = ImageInfo(
        colorInfo = ColorInfo(ColorType.BGRA_8888, ColorAlphaType.OPAQUE, ColorSpace.sRGB),
        width = width,
        height = height,
    )
    val surface = Surface.makeRaster(info)
    try {
        val paint = Paint()
        drawWith(surface.canvas, paint)
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

/** Inserts an APP1 segment right after the SOI of [outerJpeg]. */
internal fun spliceApp1IntoJpeg(outerJpeg: ByteArray, app1Body: ByteArray): ByteArray {
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
