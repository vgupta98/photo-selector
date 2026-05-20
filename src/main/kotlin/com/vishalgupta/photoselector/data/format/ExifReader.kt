package com.vishalgupta.photoselector.data.format

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal EXIF parser. Reads just enough of a JPEG to find the Orientation tag.
 * Returns 1 (normal) whenever the file isn't a JPEG, has no EXIF segment, or
 * the EXIF block is malformed. Never throws.
 */
internal object ExifReader {

    /** Number of bytes from the file head we are willing to scan for APP1. */
    private const val SCAN_LIMIT_BYTES = 256 * 1024

    /** EXIF Orientation values are 1..8. Anything else means "treat as 1". */
    fun readOrientation(path: Path): Int = try {
        Files.newInputStream(path).use { readOrientation(it) }
    } catch (_: IOException) {
        1
    } catch (_: RuntimeException) {
        1
    }

    fun readOrientation(input: InputStream): Int {
        val app1 = findApp1Payload(input) ?: return 1
        return parseOrientationFromTiff(app1)
    }

    /**
     * Scan the JPEG marker stream until an APP1 segment with the "Exif\0\0"
     * identifier is found. Returns the bytes after the identifier (the TIFF
     * block) or null if we can't find one.
     */
    private fun findApp1Payload(input: InputStream): ByteArray? {
        val b0 = input.read()
        val b1 = input.read()
        if (b0 != 0xFF || b1 != 0xD8) return null // not a JPEG SOI

        var scanned = 2
        while (scanned < SCAN_LIMIT_BYTES) {
            // Markers can be padded with 0xFF bytes; skip them.
            var marker = input.read()
            scanned++
            while (marker == 0xFF) {
                marker = input.read()
                scanned++
            }
            if (marker < 0) return null

            // SOS (0xDA) means we've reached compressed image data; stop.
            // Standalone markers (0x01, 0xD0..0xD7) have no payload.
            if (marker == 0xDA) return null
            if (marker == 0x01 || (marker in 0xD0..0xD7)) continue

            val lenHi = input.read()
            val lenLo = input.read()
            if (lenHi < 0 || lenLo < 0) return null
            val segmentLen = (lenHi shl 8) or lenLo
            if (segmentLen < 2) return null
            val payloadLen = segmentLen - 2
            scanned += 2

            if (marker == 0xE1 && payloadLen >= 6) {
                val header = ByteArray(6)
                if (!readFully(input, header)) return null
                scanned += 6
                val isExif = header[0] == 'E'.code.toByte() &&
                    header[1] == 'x'.code.toByte() &&
                    header[2] == 'i'.code.toByte() &&
                    header[3] == 'f'.code.toByte() &&
                    header[4] == 0.toByte() &&
                    header[5] == 0.toByte()
                val rest = payloadLen - 6
                if (!isExif) {
                    if (!skipFully(input, rest.toLong())) return null
                    scanned += rest
                    continue
                }
                val tiff = ByteArray(rest)
                if (!readFully(input, tiff)) return null
                return tiff
            } else {
                if (!skipFully(input, payloadLen.toLong())) return null
                scanned += payloadLen
            }
        }
        return null
    }

    private fun parseOrientationFromTiff(tiff: ByteArray): Int {
        if (tiff.size < 8) return 1
        val little = when {
            tiff[0] == 'I'.code.toByte() && tiff[1] == 'I'.code.toByte() -> true
            tiff[0] == 'M'.code.toByte() && tiff[1] == 'M'.code.toByte() -> false
            else -> return 1
        }
        val magic = u16(tiff, 2, little)
        if (magic != 0x002A) return 1

        val ifd0Offset = u32(tiff, 4, little)
        if (ifd0Offset < 8 || ifd0Offset + 2 > tiff.size) return 1

        val entryCount = u16(tiff, ifd0Offset, little)
        val entriesStart = ifd0Offset + 2
        if (entriesStart + entryCount * 12 > tiff.size) return 1

        for (i in 0 until entryCount) {
            val entry = entriesStart + i * 12
            val tag = u16(tiff, entry, little)
            if (tag != 0x0112) continue
            val type = u16(tiff, entry + 2, little)
            val count = u32(tiff, entry + 4, little)
            // Orientation is SHORT (type 3), count 1. Some files write LONG (4);
            // accept either as long as the value fits in the inline 4-byte slot.
            val value = when (type) {
                3 -> u16(tiff, entry + 8, little) // SHORT lives in the low 2 bytes
                4 -> u32(tiff, entry + 8, little)
                else -> return 1
            }
            if (count != 1) return 1
            return if (value in 1..8) value else 1
        }
        return 1
    }

    private fun u16(buf: ByteArray, offset: Int, little: Boolean): Int {
        val a = buf[offset].toInt() and 0xFF
        val b = buf[offset + 1].toInt() and 0xFF
        return if (little) (b shl 8) or a else (a shl 8) or b
    }

    private fun u32(buf: ByteArray, offset: Int, little: Boolean): Int {
        val a = buf[offset].toInt() and 0xFF
        val b = buf[offset + 1].toInt() and 0xFF
        val c = buf[offset + 2].toInt() and 0xFF
        val d = buf[offset + 3].toInt() and 0xFF
        return if (little) (d shl 24) or (c shl 16) or (b shl 8) or a
        else (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    private fun readFully(input: InputStream, out: ByteArray): Boolean {
        var read = 0
        while (read < out.size) {
            val n = input.read(out, read, out.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return false
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
        return true
    }
}
