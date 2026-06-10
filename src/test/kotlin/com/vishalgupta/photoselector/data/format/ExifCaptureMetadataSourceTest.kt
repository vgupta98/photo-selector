package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import org.junit.After
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the full EXIF -> [CaptureMetadata] chain by writing real EXIF bytes
 * (via [buildCaptureExifJpeg]) to temp files and reading them back through
 * [ExifCaptureMetadataSource].
 */
class ExifCaptureMetadataSourceTest {

    private val source = ExifCaptureMetadataSource()
    private val tempFiles = mutableListOf<Path>()

    @After fun cleanup() {
        tempFiles.forEach { runCatching { it.deleteIfExists() } }
        tempFiles.clear()
    }

    @Test fun `reads timestamp camera and orientation from full exif`() {
        val photo = write(
            buildCaptureExifJpeg(
                make = "Canon",
                model = "Canon EOS R5",
                dateTimeOriginal = "2024:01:02 03:04:05",
                subSec = "047",
                orientation = 6,
            ),
        )

        val meta = source.metadataFor(photo)

        val expectedBase = LocalDateTime
            .parse("2024:01:02 03:04:05", DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expectedBase + 47, meta.takenAtEpochMs)
        assertEquals("Canon Canon EOS R5", meta.cameraId)
        assertEquals(6, meta.orientation)
    }

    @Test fun `subsec of 5 adds half a second`() {
        val photo = write(
            buildCaptureExifJpeg(dateTimeOriginal = "2024:01:02 03:04:05", subSec = "5"),
        )

        val meta = source.metadataFor(photo)

        val base = LocalDateTime
            .parse("2024:01:02 03:04:05", DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(base + 500, meta.takenAtEpochMs)
    }

    @Test fun `make only yields camera id without model and no timestamp`() {
        val photo = write(buildCaptureExifJpeg(make = "Apple"))

        val meta = source.metadataFor(photo)

        assertEquals("Apple", meta.cameraId)
        assertNull(meta.takenAtEpochMs)
    }

    @Test fun `non-jpeg file yields NONE`() {
        val photo = write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))

        assertEquals(CaptureMetadata.NONE, source.metadataFor(photo))
    }

    private fun write(bytes: ByteArray): Photo {
        val path = Files.createTempFile("capture-meta-", ".jpg").also {
            tempFiles.add(it)
            it.writeBytes(bytes)
        }
        return Photo(
            id = PhotoId(path.fileName.toString()),
            absolutePath = path,
            relativePath = path.fileName.toString(),
            fileName = path.fileName.toString(),
            sizeBytes = bytes.size.toLong(),
            lastModifiedEpochMs = 0,
        )
    }
}
