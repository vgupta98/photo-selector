package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadataSource
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CompositeCaptureMetadataSourceTest {

    private val photo = photo("x.jpg")
    private val jpegMeta = CaptureMetadata(takenAtEpochMs = 111, cameraId = "Canon", orientation = 1)
    private val heicMeta = CaptureMetadata(takenAtEpochMs = 222, cameraId = "Apple", orientation = 6)

    @Test fun `returns the first non-NONE result`() {
        val composite = CompositeCaptureMetadataSource(
            listOf(
                CaptureMetadataSource { CaptureMetadata.NONE },
                CaptureMetadataSource { heicMeta },
            ),
        )
        assertEquals(heicMeta, composite.metadataFor(photo))
    }

    @Test fun `earlier source wins and later sources are not consulted`() {
        var secondConsulted = false
        val composite = CompositeCaptureMetadataSource(
            listOf(
                CaptureMetadataSource { jpegMeta },
                CaptureMetadataSource { secondConsulted = true; heicMeta },
            ),
        )
        assertEquals(jpegMeta, composite.metadataFor(photo))
        assertFalse(secondConsulted, "short-circuit on first hit")
    }

    @Test fun `all-NONE yields NONE`() {
        val composite = CompositeCaptureMetadataSource(
            listOf(
                CaptureMetadataSource { CaptureMetadata.NONE },
                CaptureMetadataSource { CaptureMetadata.NONE },
            ),
        )
        assertEquals(CaptureMetadata.NONE, composite.metadataFor(photo))
    }

    @Test fun `empty source list yields NONE`() {
        assertEquals(CaptureMetadata.NONE, CompositeCaptureMetadataSource(emptyList()).metadataFor(photo))
    }

    private fun photo(name: String) = Photo(
        id = PhotoId(name),
        absolutePath = Path.of(name),
        relativePath = name,
        fileName = name,
        sizeBytes = 0,
        lastModifiedEpochMs = 0,
    )
}
