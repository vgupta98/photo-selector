package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the platform-independent behaviour: format scoping and fail-soft. The actual ImageIO read of
 * a real HEIC is macOS-only and validated against real files (see CLAUDE.md's HEIC carve-out); it
 * cannot be unit-tested from a synthesized fixture because the EXIF builders emit header-only JPEGs.
 */
class HeicCaptureMetadataSourceTest {

    private val source = HeicCaptureMetadataSource()

    @Test fun `non-heic file is NONE without touching the native bridge`() {
        // .jpg/.png are not this source's job; it must short-circuit before any ImageIO call so it
        // composes cleanly with the JPEG source. A missing path would throw if the bridge were hit.
        assertEquals(CaptureMetadata.NONE, source.metadataFor(photo("/no/such/file.jpg")))
        assertEquals(CaptureMetadata.NONE, source.metadataFor(photo("/no/such/file.png")))
        assertEquals(CaptureMetadata.NONE, source.metadataFor(photo("/no/such/file.dng")))
    }

    @Test fun `unreadable heic degrades to NONE and never throws`() {
        // A .heic that doesn't exist: off macOS the bridge no-ops; on macOS the framework call fails
        // and is caught. Either way the contract is NONE, not an exception.
        assertEquals(CaptureMetadata.NONE, source.metadataFor(photo("/no/such/file.heic")))
        assertEquals(CaptureMetadata.NONE, source.metadataFor(photo("/no/such/file.HEIF")))
    }

    private fun photo(absolute: String): Photo {
        val path = Path.of(absolute)
        val name = path.fileName.toString()
        return Photo(
            id = PhotoId(name),
            absolutePath = path,
            relativePath = name,
            fileName = name,
            sizeBytes = 0,
            lastModifiedEpochMs = 0,
        )
    }
}
