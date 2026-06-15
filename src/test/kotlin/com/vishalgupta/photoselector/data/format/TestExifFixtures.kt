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

/**
 * Builds a SOI + APP1(Exif) JPEG carrying capture metadata: IFD0 Make / Model /
 * Orientation plus an EXIF SubIFD (pointer tag 0x8769) holding DateTimeOriginal
 * and SubSecTimeOriginal. ASCII values that don't fit in the 4-byte value field
 * are placed in a data area after the IFDs and referenced by offset, exactly as
 * [ExifReader.readCaptureInfo] expects. No scan data — decodable by the reader,
 * not by Skia.
 */
internal fun buildCaptureExifJpeg(
    littleEndian: Boolean = true,
    make: String? = null,
    model: String? = null,
    dateTimeOriginal: String? = null,
    subSec: String? = null,
    orientation: Int? = null,
): ByteArray {
    val body = "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) +
        buildCaptureTiff(littleEndian, make, model, dateTimeOriginal, subSec, orientation)
    val app1Length = body.size + 2
    require(app1Length <= 0xFFFF)
    return ByteBuffer.allocate(2 + 2 + app1Length).order(ByteOrder.BIG_ENDIAN)
        .putShort(0xFFD8.toShort())
        .putShort(0xFFE1.toShort())
        .putShort(app1Length.toShort())
        .put(body)
        .array()
}

private class TiffEntry(
    val tag: Int,
    val type: Int,
    val count: Int,
    /** 4-byte value field when the value is inline; null when it lives in the data area. */
    val inlineValue: ByteArray?,
    /** Payload placed in the data area when [inlineValue] is null. */
    val data: ByteArray?,
)

private fun buildCaptureTiff(
    littleEndian: Boolean,
    make: String?,
    model: String?,
    dateTimeOriginal: String?,
    subSec: String?,
    orientation: Int?,
): ByteArray {
    val order = if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

    fun ascii(tag: Int, s: String): TiffEntry {
        val bytes = s.toByteArray(Charsets.US_ASCII) + 0
        return if (bytes.size <= 4) TiffEntry(tag, 2, bytes.size, bytes.copyOf(4), null)
        else TiffEntry(tag, 2, bytes.size, null, bytes)
    }
    fun short(tag: Int, v: Int) = TiffEntry(
        tag, 3, 1,
        ByteBuffer.allocate(4).order(order).putShort(v.toShort()).putShort(0).array(), null,
    )
    fun long(tag: Int, v: Int) = TiffEntry(
        tag, 4, 1, ByteBuffer.allocate(4).order(order).putInt(v).array(), null,
    )

    val subEntries = buildList {
        dateTimeOriginal?.let { add(ascii(0x9003, it)) }
        subSec?.let { add(ascii(0x9291, it)) }
    }
    val hasSub = subEntries.isNotEmpty()

    val ifd0Base = buildList {
        make?.let { add(ascii(0x010F, it)) }
        model?.let { add(ascii(0x0110, it)) }
        orientation?.let { add(short(0x0112, it)) }
    }

    val ifd0Count = ifd0Base.size + if (hasSub) 1 else 0
    val ifd0Size = 2 + ifd0Count * 12 + 4
    val subOffset = 8 + ifd0Size
    val subSize = if (hasSub) 2 + subEntries.size * 12 + 4 else 0
    val dataStart = 8 + ifd0Size + subSize

    // SubIFD pointer (tag 0x8769) is the largest tag, so it goes last in IFD0.
    val ifd0Entries = if (hasSub) ifd0Base + long(0x8769, subOffset) else ifd0Base

    // Resolve data-area offsets for deferred (long-ASCII) entries in write order.
    val dataBlob = java.io.ByteArrayOutputStream()
    val valueFields = HashMap<TiffEntry, ByteArray>()
    fun resolve(entries: List<TiffEntry>) {
        for (e in entries) {
            valueFields[e] = e.inlineValue ?: run {
                val off = dataStart + dataBlob.size()
                dataBlob.write(e.data!!)
                ByteBuffer.allocate(4).order(order).putInt(off).array()
            }
        }
    }
    resolve(ifd0Entries)
    resolve(subEntries)

    val buf = ByteBuffer.allocate(dataStart + dataBlob.size()).order(order)
    if (littleEndian) { buf.put(0x49); buf.put(0x49) } else { buf.put(0x4D); buf.put(0x4D) }
    buf.putShort(42)
    buf.putInt(8) // IFD0 at offset 8

    fun writeIfd(entries: List<TiffEntry>, nextIfd: Int) {
        buf.putShort(entries.size.toShort())
        for (e in entries) {
            buf.putShort(e.tag.toShort())
            buf.putShort(e.type.toShort())
            buf.putInt(e.count)
            buf.put(valueFields.getValue(e)) // raw 4 bytes, already endian-encoded
        }
        buf.putInt(nextIfd)
    }
    writeIfd(ifd0Entries, 0)
    if (hasSub) writeIfd(subEntries, 0)
    buf.put(dataBlob.toByteArray())

    return buf.array()
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
