package com.vishalgupta.photoselector.data.format

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal data class EmbeddedJpegThumb(
    val bytes: ByteArray,
    /** EXIF Orientation tag value (1..8). Defaults to 1 when absent or out of range. */
    val orientation: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is EmbeddedJpegThumb && orientation == other.orientation && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * orientation + bytes.contentHashCode()
}

/**
 * Capture metadata read out of a JPEG's EXIF block: the shot timestamp and the
 * camera identity used by burst grouping. All fields are raw EXIF values, left
 * for callers to interpret; any field is null when its tag is absent or
 * malformed (the reader never throws).
 */
internal data class ExifCaptureInfo(
    /** Raw EXIF DateTimeOriginal, e.g. "2024:01:02 03:04:05"; null if absent. */
    val dateTimeOriginal: String?,
    /** Raw EXIF SubSecTimeOriginal fractional digits, e.g. "047"; null if absent. */
    val subSecTimeOriginal: String?,
    /** EXIF Make, trimmed; null if absent. */
    val make: String?,
    /** EXIF Model, trimmed; null if absent. */
    val model: String?,
    /** IFD0 Orientation (1..8); null if absent or out of range. */
    val orientation: Int?,
)

/**
 * Reads selected tags out of a JPEG's EXIF APP1 segment. Two entry points share
 * the same segment walk and TIFF/IFD machinery:
 *
 * - [readEmbeddedThumbnail] — the IFD1 JPEG thumbnail (+ the IFD0 Orientation
 *   that applies to it), for the decode fast path.
 * - [readCaptureInfo] — IFD0 Make/Model/Orientation and the EXIF SubIFD's
 *   DateTimeOriginal/SubSecTimeOriginal, for burst grouping.
 *
 * Both return null for anything that isn't a JPEG with a well-formed EXIF block.
 * Scope is deliberately narrow: only the tags these two features need are read;
 * everything else is ignored. Malformed bytes yield null, never an exception.
 */
internal object ExifReader {

    private const val SOI = 0xFFD8
    private const val APP1 = 0xE1
    private const val SOS = 0xDA
    private const val EOI = 0xD9

    private const val TAG_MAKE = 0x010F
    private const val TAG_MODEL = 0x0110
    private const val TAG_ORIENTATION = 0x0112
    private const val TAG_JPEG_INTERCHANGE_FORMAT = 0x0201
    private const val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202
    private const val TAG_EXIF_SUBIFD = 0x8769
    private const val TAG_DATETIME_ORIGINAL = 0x9003
    private const val TAG_SUBSEC_TIME_ORIGINAL = 0x9291

    private const val TYPE_ASCII = 2
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4

    fun readEmbeddedThumbnail(path: Path): EmbeddedJpegThumb? =
        runCatching {
            Files.newInputStream(path).buffered().use { parse(it) }
        }.getOrNull()

    fun readCaptureInfo(path: Path): ExifCaptureInfo? =
        runCatching {
            Files.newInputStream(path).buffered().use { parseCapture(it) }
        }.getOrNull()

    internal fun parse(input: InputStream): EmbeddedJpegThumb? =
        runCatching { findExifApp1Body(input)?.let { parseThumb(it) } }.getOrNull()

    internal fun parseCapture(input: InputStream): ExifCaptureInfo? =
        runCatching { findExifApp1Body(input)?.let { parseCaptureInfo(it) } }.getOrNull()

    /**
     * Walks the JPEG marker segments and returns the body of the first EXIF APP1
     * (the bytes after the 2-byte length, starting with "Exif\0\0"), or
     * null. Non-EXIF APP1 segments (e.g. XMP) are skipped.
     */
    private fun findExifApp1Body(input: InputStream): ByteArray? {
        val dis = DataInputStream(input)
        if (dis.readUnsignedShort() != SOI) return null

        while (true) {
            val marker = readMarker(dis) ?: return null
            when (marker) {
                SOS, EOI -> return null
                in 0xD0..0xD7, 0x01 -> {
                    // RSTn and TEM markers carry no length payload and have no business
                    // appearing before the EXIF segment in a well-formed JPEG.
                    return null
                }
                APP1 -> {
                    val len = dis.readUnsignedShort()
                    if (len < 2) return null
                    val body = ByteArray(len - 2)
                    dis.readFully(body)
                    if (isExifBody(body)) return body
                    // Wasn't the EXIF APP1 (e.g. XMP). Keep walking.
                }
                else -> {
                    val len = dis.readUnsignedShort()
                    if (len < 2) return null
                    dis.skipNBytes((len - 2).toLong())
                }
            }
        }
    }

    /** Reads `0xFF`-prefixed marker; tolerates the EXIF-legal padding of extra `0xFF` fill bytes. */
    private fun readMarker(dis: DataInputStream): Int? {
        return try {
            var b = dis.readUnsignedByte()
            if (b != 0xFF) return null
            while (b == 0xFF) b = dis.readUnsignedByte()
            // 0x00 is the stuffed byte inside scan data and never a marker.
            if (b == 0x00) null else b
        } catch (_: EOFException) {
            null
        }
    }

    /** "Exif\0\0" prefix plus enough room for the TIFF header. */
    private fun isExifBody(body: ByteArray): Boolean =
        body.size >= 6 + 8 &&
            body[0] == 0x45.toByte() && body[1] == 0x78.toByte() &&
            body[2] == 0x69.toByte() && body[3] == 0x66.toByte() &&
            body[4] == 0x00.toByte() && body[5] == 0x00.toByte()

    private fun parseThumb(body: ByteArray): EmbeddedJpegThumb? {
        val start = tiffStart(body) ?: return null
        val view = start.view
        val tiffBase = start.tiffBase

        val ifd0 = readIfd(view, tiffBase, start.ifd0Abs, setOf(TAG_ORIENTATION)) ?: return null
        val orientation = ifd0.ints[TAG_ORIENTATION]?.takeIf { it in 1..8 } ?: 1

        val ifd1Abs = if (ifd0.nextIfdOffset > 0) tiffBase + ifd0.nextIfdOffset else return null
        if (ifd1Abs < 0 || ifd1Abs >= body.size) return null

        val ifd1 = readIfd(
            view,
            tiffBase,
            ifd1Abs,
            setOf(TAG_JPEG_INTERCHANGE_FORMAT, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH),
        ) ?: return null

        val thumbOffset = ifd1.ints[TAG_JPEG_INTERCHANGE_FORMAT] ?: return null
        val thumbLength = ifd1.ints[TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] ?: return null
        if (thumbLength <= 0) return null

        val thumbStart = tiffBase + thumbOffset
        val thumbEnd = thumbStart + thumbLength
        if (thumbStart < 0 || thumbEnd > body.size || thumbEnd < thumbStart) return null

        val thumbBytes = body.copyOfRange(thumbStart, thumbEnd)
        if (!looksLikeJpeg(thumbBytes)) return null

        return EmbeddedJpegThumb(thumbBytes, orientation)
    }

    private fun parseCaptureInfo(body: ByteArray): ExifCaptureInfo? {
        val start = tiffStart(body) ?: return null
        val view = start.view
        val tiffBase = start.tiffBase

        val ifd0 = readIfd(
            view,
            tiffBase,
            start.ifd0Abs,
            setOf(TAG_MAKE, TAG_MODEL, TAG_ORIENTATION, TAG_EXIF_SUBIFD),
        ) ?: return null

        val make = ifd0.strings[TAG_MAKE]
        val model = ifd0.strings[TAG_MODEL]
        val orientation = ifd0.ints[TAG_ORIENTATION]?.takeIf { it in 1..8 }

        var dateTimeOriginal: String? = null
        var subSecTimeOriginal: String? = null
        val subOffset = ifd0.ints[TAG_EXIF_SUBIFD]
        if (subOffset != null) {
            val subAbs = tiffBase + subOffset
            if (subAbs in 0 until body.size) {
                readIfd(
                    view,
                    tiffBase,
                    subAbs,
                    setOf(TAG_DATETIME_ORIGINAL, TAG_SUBSEC_TIME_ORIGINAL),
                )?.let { sub ->
                    dateTimeOriginal = sub.strings[TAG_DATETIME_ORIGINAL]
                    subSecTimeOriginal = sub.strings[TAG_SUBSEC_TIME_ORIGINAL]
                }
            }
        }

        if (make == null && model == null && dateTimeOriginal == null && orientation == null) return null
        return ExifCaptureInfo(dateTimeOriginal, subSecTimeOriginal, make, model, orientation)
    }

    private data class TiffStart(val view: ByteView, val tiffBase: Int, val ifd0Abs: Int)

    /** Validates the TIFF header at offset 6 (caller guarantees the "Exif\0\0" prefix). */
    private fun tiffStart(body: ByteArray): TiffStart? {
        val tiffBase = 6
        if (tiffBase + 8 > body.size) return null
        val littleEndian = when {
            body[tiffBase] == 0x49.toByte() && body[tiffBase + 1] == 0x49.toByte() -> true
            body[tiffBase] == 0x4D.toByte() && body[tiffBase + 1] == 0x4D.toByte() -> false
            else -> return null
        }
        val view = ByteView(body, littleEndian)
        if (view.u16(tiffBase + 2) != 42) return null
        val ifd0Abs = tiffBase + view.u32(tiffBase + 4)
        if (ifd0Abs < 0 || ifd0Abs >= body.size) return null
        return TiffStart(view, tiffBase, ifd0Abs)
    }

    private data class Ifd(
        val ints: Map<Int, Int>,
        val strings: Map<Int, String>,
        val nextIfdOffset: Int,
    )

    private fun readIfd(view: ByteView, tiffBase: Int, ifdAbs: Int, want: Set<Int>): Ifd? {
        if (ifdAbs + 2 > view.size) return null
        val entryCount = view.u16(ifdAbs)
        val entriesStart = ifdAbs + 2
        val nextIfdAt = entriesStart + entryCount * 12
        if (nextIfdAt + 4 > view.size) return null

        val ints = HashMap<Int, Int>(want.size)
        val strings = HashMap<Int, String>(want.size)
        for (i in 0 until entryCount) {
            val entryAbs = entriesStart + i * 12
            val tag = view.u16(entryAbs)
            if (tag !in want) continue
            val type = view.u16(entryAbs + 2)
            val count = view.u32(entryAbs + 4)
            when (type) {
                TYPE_SHORT -> if (count == 1) ints[tag] = view.u16(entryAbs + 8)
                TYPE_LONG -> if (count == 1) ints[tag] = view.u32(entryAbs + 8)
                TYPE_ASCII -> readAscii(view, tiffBase, entryAbs + 8, count)?.let { strings[tag] = it }
                else -> {} // other types are not needed by either feature
            }
        }
        val nextOffset = view.u32(nextIfdAt)
        return Ifd(ints, strings, nextOffset)
    }

    /**
     * Reads an ASCII value. EXIF stores it inline in the 4-byte value field when
     * it fits (count <= 4), otherwise at an offset relative to the TIFF base.
     */
    private fun readAscii(view: ByteView, tiffBase: Int, valueFieldAbs: Int, count: Int): String? {
        if (count <= 0) return null
        val dataAbs = if (count <= 4) valueFieldAbs else tiffBase + view.u32(valueFieldAbs)
        return view.ascii(dataAbs, count)
    }

    private fun looksLikeJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()

    private class ByteView(private val buf: ByteArray, private val littleEndian: Boolean) {
        val size: Int get() = buf.size

        fun u16(at: Int): Int {
            if (at < 0 || at + 2 > buf.size) throw IndexOutOfBoundsException()
            val a = buf[at].toInt() and 0xFF
            val b = buf[at + 1].toInt() and 0xFF
            return if (littleEndian) (b shl 8) or a else (a shl 8) or b
        }

        fun u32(at: Int): Int {
            if (at < 0 || at + 4 > buf.size) throw IndexOutOfBoundsException()
            val a = buf[at].toInt() and 0xFF
            val b = buf[at + 1].toInt() and 0xFF
            val c = buf[at + 2].toInt() and 0xFF
            val d = buf[at + 3].toInt() and 0xFF
            // EXIF offsets are always non-negative; values that overflow Int are pathological.
            // Returning a negative int here just means the bounds check below rejects the file.
            return if (littleEndian) (d shl 24) or (c shl 16) or (b shl 8) or a
            else (a shl 24) or (b shl 16) or (c shl 8) or d
        }

        /** Reads up to [count] bytes as US-ASCII, stopping at the NUL terminator. */
        fun ascii(at: Int, count: Int): String? {
            // `count > buf.size - at` rather than `at + count > buf.size`: a hostile offset (u32 can
            // return a large/negative Int) could overflow the addition and slip past the bound.
            if (at < 0 || count < 0 || at > buf.size || count > buf.size - at) return null
            var end = at
            val limit = at + count
            while (end < limit && buf[end].toInt() != 0) end++
            return String(buf, at, end - at, Charsets.US_ASCII).trim().ifEmpty { null }
        }
    }
}
