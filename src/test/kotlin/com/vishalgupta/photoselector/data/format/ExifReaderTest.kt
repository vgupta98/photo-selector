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

    // --- capture info (Make/Model/DateTimeOriginal/SubSecTimeOriginal) ---

    @Test fun `capture happy path little-endian reads all fields`() {
        val bytes = buildCaptureExifJpeg(
            littleEndian = true,
            make = "Canon",
            model = "Canon EOS R5",
            dateTimeOriginal = "2024:01:02 03:04:05",
            subSec = "047",
            orientation = 6,
        )
        val info = ExifReader.parseCapture(ByteArrayInputStream(bytes))!!
        assertEquals("Canon", info.make)
        assertEquals("Canon EOS R5", info.model)
        assertEquals("2024:01:02 03:04:05", info.dateTimeOriginal)
        assertEquals("047", info.subSecTimeOriginal)
        assertEquals(6, info.orientation)
    }

    @Test fun `capture happy path big-endian reads all fields`() {
        val bytes = buildCaptureExifJpeg(
            littleEndian = false,
            make = "NIKON CORPORATION",
            model = "NIKON Z 6",
            dateTimeOriginal = "2023:12:25 18:30:00",
            subSec = "5",
        )
        val info = ExifReader.parseCapture(ByteArrayInputStream(bytes))!!
        assertEquals("NIKON CORPORATION", info.make)
        assertEquals("NIKON Z 6", info.model)
        assertEquals("2023:12:25 18:30:00", info.dateTimeOriginal)
        assertEquals("5", info.subSecTimeOriginal)
    }

    @Test fun `capture reads inline ascii that fits the value field`() {
        // "HTC" + NUL == 4 bytes -> stored inline, not at an offset.
        val bytes = buildCaptureExifJpeg(make = "HTC", model = "One")
        val info = ExifReader.parseCapture(ByteArrayInputStream(bytes))!!
        assertEquals("HTC", info.make)
        assertEquals("One", info.model)
    }

    @Test fun `capture without sub-ifd still reads make and model`() {
        val bytes = buildCaptureExifJpeg(make = "Apple", model = "iPhone 15 Pro")
        val info = ExifReader.parseCapture(ByteArrayInputStream(bytes))!!
        assertEquals("Apple", info.make)
        assertEquals("iPhone 15 Pro", info.model)
        assertNull(info.dateTimeOriginal)
        assertNull(info.subSecTimeOriginal)
    }

    @Test fun `capture datetime without subsec`() {
        val bytes = buildCaptureExifJpeg(
            make = "Sony",
            dateTimeOriginal = "2022:06:01 09:00:00",
        )
        val info = ExifReader.parseCapture(ByteArrayInputStream(bytes))!!
        assertEquals("2022:06:01 09:00:00", info.dateTimeOriginal)
        assertNull(info.subSecTimeOriginal)
    }

    @Test fun `capture with only orientation still returns info`() {
        val bytes = buildCaptureExifJpeg(orientation = 3)
        val info = ExifReader.parseCapture(ByteArrayInputStream(bytes))!!
        assertEquals(3, info.orientation)
        assertNull(info.make)
        assertNull(info.dateTimeOriginal)
    }

    @Test fun `capture on a non-JPEG returns null`() {
        assertNull(ExifReader.parseCapture(ByteArrayInputStream(byteArrayOf(0x12, 0x34, 0x56, 0x78))))
    }

    @Test fun `capture on JPEG with no APP1 returns null`() {
        assertNull(ExifReader.parseCapture(ByteArrayInputStream(tinyJpeg)))
    }

    @Test fun `capture skips non-EXIF app1 then reads the real one`() {
        val nonExifApp1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x09, 'X'.code.toByte(), 'M'.code.toByte(),
            'P'.code.toByte(), 0x00, 0x01, 0x02, 0x03,
        )
        val real = buildCaptureExifJpeg(make = "Fujifilm", model = "X-T5")
        val merged = real.copyOfRange(0, 2) + nonExifApp1 + real.copyOfRange(2, real.size)
        val info = ExifReader.parseCapture(ByteArrayInputStream(merged))!!
        assertEquals("Fujifilm", info.make)
        assertEquals("X-T5", info.model)
    }
}

