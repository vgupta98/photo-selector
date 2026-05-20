package com.vishalgupta.photoselector.data.format

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-byte tests for [ExifReader]. No Skia involvement — we hand-build EXIF
 * payloads and assert the parser does what we expect.
 *
 * For the end-to-end "does the fast path actually decode pixels" test, see
 * [JpegDecoderTest], which synthesises a real-Skia-encoded thumbnail and runs
 * it through [JpegDecoder].
 */
class ExifReaderTest {

    private val tinyJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    @Test fun `happy path little-endian extracts thumb and orientation`() {
        val bytes = ExifFixtureBuilder(littleEndian = true)
            .orientation(6)
            .thumbnail(tinyJpeg)
            .buildJpeg()

        val parsed = ExifReader.parse(ByteArrayInputStream(bytes))!!
        assertEquals(6, parsed.orientation)
        assertContentEquals(tinyJpeg, parsed.bytes)
    }

    @Test fun `happy path big-endian extracts thumb and orientation`() {
        val bytes = ExifFixtureBuilder(littleEndian = false)
            .orientation(8)
            .thumbnail(tinyJpeg)
            .buildJpeg()

        val parsed = ExifReader.parse(ByteArrayInputStream(bytes))!!
        assertEquals(8, parsed.orientation)
        assertContentEquals(tinyJpeg, parsed.bytes)
    }

    @Test fun `missing orientation tag defaults to 1`() {
        val bytes = ExifFixtureBuilder()
            .thumbnail(tinyJpeg)
            .buildJpeg()

        val parsed = ExifReader.parse(ByteArrayInputStream(bytes))!!
        assertEquals(1, parsed.orientation)
    }

    @Test fun `out-of-range orientation defaults to 1`() {
        val bytes = ExifFixtureBuilder()
            .orientation(99)
            .thumbnail(tinyJpeg)
            .buildJpeg()

        val parsed = ExifReader.parse(ByteArrayInputStream(bytes))!!
        assertEquals(1, parsed.orientation)
    }

    @Test fun `not a JPEG returns null`() {
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertNull(ExifReader.parse(ByteArrayInputStream(bytes)))
    }

    @Test fun `JPEG with no APP1 returns null`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertNull(ExifReader.parse(ByteArrayInputStream(bytes)))
    }

    @Test fun `APP1 with non-EXIF signature is skipped`() {
        // First APP1 holds bogus XMP-looking content; second APP1 is the real EXIF one.
        val nonExifApp1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x09, 'X'.code.toByte(), 'M'.code.toByte(),
            'P'.code.toByte(), 0x00, 0x01, 0x02, 0x03,
        )
        val realExif = ExifFixtureBuilder().thumbnail(tinyJpeg).buildJpeg()
        val merged = realExif.copyOfRange(0, 2) + nonExifApp1 + realExif.copyOfRange(2, realExif.size)

        val parsed = ExifReader.parse(ByteArrayInputStream(merged))!!
        assertContentEquals(tinyJpeg, parsed.bytes)
    }

    @Test fun `APP1 with no IFD1 returns null`() {
        val bytes = ExifFixtureBuilder().orientation(1).buildJpeg() // no thumbnail call → no IFD1
        assertNull(ExifReader.parse(ByteArrayInputStream(bytes)))
    }

    @Test fun `thumb offset past end returns null`() {
        // Build a normal one, then stomp the thumb-offset entry to point past the buffer.
        val bytes = ExifFixtureBuilder().thumbnail(tinyJpeg).buildJpeg()
        // The thumb offset is stored as a LONG in IFD1's first entry; finding its exact
        // byte location is brittle, so we just truncate the file mid-thumb instead.
        val truncated = bytes.copyOfRange(0, bytes.size - 2)
        assertNull(ExifReader.parse(ByteArrayInputStream(truncated)))
    }

    @Test fun `thumb that does not look like JPEG returns null`() {
        val bytes = ExifFixtureBuilder()
            .thumbnail(byteArrayOf(0x00, 0x01, 0x02, 0x03))
            .buildJpeg()
        assertNull(ExifReader.parse(ByteArrayInputStream(bytes)))
    }

    @Test fun `all 8 orientations round-trip`() {
        for (o in 1..8) {
            val bytes = ExifFixtureBuilder().orientation(o).thumbnail(tinyJpeg).buildJpeg()
            val parsed = ExifReader.parse(ByteArrayInputStream(bytes))!!
            assertEquals(o, parsed.orientation, "orientation $o")
        }
    }
}

