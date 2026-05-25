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
 * Reads the embedded JPEG thumbnail (and the parent IFD0 Orientation tag that applies to it)
 * out of a JPEG's EXIF APP1 segment.
 *
 * Returns null for anything that isn't a JPEG with a complete, well-formed EXIF + IFD1 thumbnail.
 * Callers fall back to the full-resolution decode in that case.
 *
 * Scope is deliberately narrow: only the three tags this PR needs
 * (0x0112 Orientation, 0x0201 JPEGInterchangeFormat, 0x0202 JPEGInterchangeFormatLength).
 * Other tags are ignored.
 */
internal object ExifReader {

    private const val SOI = 0xFFD8
    private const val APP1 = 0xE1
    private const val SOS = 0xDA
    private const val EOI = 0xD9

    private const val TAG_ORIENTATION = 0x0112
    private const val TAG_JPEG_INTERCHANGE_FORMAT = 0x0201
    private const val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202

    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4

    fun readEmbeddedThumbnail(path: Path): EmbeddedJpegThumb? =
        runCatching {
            Files.newInputStream(path).buffered().use { parse(it) }
        }.getOrNull()

    internal fun parse(input: InputStream): EmbeddedJpegThumb? =
        runCatching { parseUnsafe(input) }.getOrNull()

    private fun parseUnsafe(input: InputStream): EmbeddedJpegThumb? {
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
                    parseApp1(body)?.let { return it }
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

    private fun parseApp1(body: ByteArray): EmbeddedJpegThumb? {
        // "Exif\0\0"
        if (body.size < 6 + 8) return null
        if (body[0] != 0x45.toByte() || body[1] != 0x78.toByte() ||
            body[2] != 0x69.toByte() || body[3] != 0x66.toByte() ||
            body[4] != 0x00.toByte() || body[5] != 0x00.toByte()
        ) return null

        val tiffBase = 6
        val littleEndian = when {
            body[tiffBase] == 0x49.toByte() && body[tiffBase + 1] == 0x49.toByte() -> true
            body[tiffBase] == 0x4D.toByte() && body[tiffBase + 1] == 0x4D.toByte() -> false
            else -> return null
        }
        val view = ByteView(body, littleEndian)

        if (view.u16(tiffBase + 2) != 42) return null
        val ifd0Offset = view.u32(tiffBase + 4)
        val ifd0Abs = tiffBase + ifd0Offset
        if (ifd0Abs < 0 || ifd0Abs >= body.size) return null

        val ifd0 = readIfd(view, tiffBase, ifd0Abs, setOf(TAG_ORIENTATION)) ?: return null
        val orientation = ifd0.values[TAG_ORIENTATION]?.toInt()?.takeIf { it in 1..8 } ?: 1

        val ifd1Abs = if (ifd0.nextIfdOffset > 0) tiffBase + ifd0.nextIfdOffset else return null
        if (ifd1Abs < 0 || ifd1Abs >= body.size) return null

        val ifd1 = readIfd(
            view,
            tiffBase,
            ifd1Abs,
            setOf(TAG_JPEG_INTERCHANGE_FORMAT, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH),
        ) ?: return null

        val thumbOffset = ifd1.values[TAG_JPEG_INTERCHANGE_FORMAT] ?: return null
        val thumbLength = ifd1.values[TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] ?: return null
        if (thumbLength <= 0) return null

        val thumbStart = tiffBase + thumbOffset
        val thumbEnd = thumbStart + thumbLength
        if (thumbStart < 0 || thumbEnd > body.size || thumbEnd < thumbStart) return null

        val thumbBytes = body.copyOfRange(thumbStart, thumbEnd)
        if (!looksLikeJpeg(thumbBytes)) return null

        return EmbeddedJpegThumb(thumbBytes, orientation)
    }

    private data class Ifd(val values: Map<Int, Int>, val nextIfdOffset: Int)

    private fun readIfd(view: ByteView, tiffBase: Int, ifdAbs: Int, want: Set<Int>): Ifd? {
        if (ifdAbs + 2 > view.size) return null
        val entryCount = view.u16(ifdAbs)
        val entriesStart = ifdAbs + 2
        val nextIfdAt = entriesStart + entryCount * 12
        if (nextIfdAt + 4 > view.size) return null

        val collected = HashMap<Int, Int>(want.size)
        for (i in 0 until entryCount) {
            val entryAbs = entriesStart + i * 12
            val tag = view.u16(entryAbs)
            if (tag !in want) continue
            val type = view.u16(entryAbs + 2)
            val count = view.u32(entryAbs + 4)
            if (count != 1) continue
            val value = when (type) {
                TYPE_SHORT -> view.u16(entryAbs + 8)
                TYPE_LONG -> view.u32(entryAbs + 8)
                else -> continue
            }
            collected[tag] = value
        }
        val nextOffset = view.u32(nextIfdAt)
        return Ifd(collected, nextOffset)
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
    }
}
