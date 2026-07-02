package com.vishalgupta.photoselector.data.export

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XmpSidecarPhotoExporterTest {

    // --- The precedence rule (reject wins, then favourite, else nothing) -----------------------

    @Test
    fun `reject wins over favourite`() {
        assertEquals(XmpMapping.REJECTED, xmpMappingFor(isRejected = true, isFavourite = true))
        assertEquals(XmpMapping.REJECTED, xmpMappingFor(isRejected = true, isFavourite = false))
    }

    @Test
    fun `favourite maps to five stars when not rejected`() {
        assertEquals(XmpMapping.FAVOURITE, xmpMappingFor(isRejected = false, isFavourite = true))
    }

    @Test
    fun `neither bucket maps to none`() {
        assertEquals(XmpMapping.NONE, xmpMappingFor(isRejected = false, isFavourite = false))
    }

    // --- The generated packet is well-formed and carries the right Rating/Label ----------------

    @Test
    fun `reject packet is well-formed with rating minus one and label Rejected`() {
        val desc = parseDescription(buildXmpPacket(XmpMapping.REJECTED.rating, XmpMapping.REJECTED.label))
        assertEquals("-1", desc.getAttribute("xmp:Rating"))
        assertEquals("Rejected", desc.getAttribute("xmp:Label"))
    }

    @Test
    fun `favourite packet is well-formed with rating five and no label`() {
        val desc = parseDescription(buildXmpPacket(XmpMapping.FAVOURITE.rating, XmpMapping.FAVOURITE.label))
        assertEquals("5", desc.getAttribute("xmp:Rating"))
        assertFalse(desc.hasAttribute("xmp:Label"))
    }

    @Test
    fun `none packet is well-formed with neither rating nor label`() {
        val desc = parseDescription(buildXmpPacket(XmpMapping.NONE.rating, XmpMapping.NONE.label))
        assertFalse(desc.hasAttribute("xmp:Rating"))
        assertFalse(desc.hasAttribute("xmp:Label"))
    }

    @Test
    fun `label with XML metacharacters is escaped and round-trips through a parser`() {
        // Synthetic label (the shipped one is the constant "Rejected"), proving the escaper keeps the
        // packet well-formed and the DAM reads back the original text unescaped.
        val raw = "a<b&c\"d>e"
        val desc = parseDescription(buildXmpPacket(rating = 5, label = raw))
        assertEquals(raw, desc.getAttribute("xmp:Label"))
    }

    // --- The exporter writes sidecars next to originals with correct naming --------------------

    @Test
    fun `writes an xmp sidecar next to each rated photo and skips the unrated`() = runTest {
        val dir = createTempDirectory("xmp-test")
        try {
            val fav = photo(dir, "IMG_1234.CR2", "fav")
            val rej = photo(dir, "IMG_5678.JPG", "rej")
            val plain = photo(dir, "IMG_9999.HEIC", "plain")

            val report = XmpSidecarPhotoExporter().exportSidecars(
                root = RootFolder(dir),
                photos = listOf(fav, rej, plain),
                favouriteIds = setOf(PhotoId("fav")),
                rejectedIds = setOf(PhotoId("rej")),
                onProgress = { _, _ -> },
            )

            assertEquals(2, report.written)
            assertEquals(1, report.skipped)
            assertTrue(report.failed.isEmpty())

            // Sidecar shares the basename, extension swapped to .xmp, in the same directory.
            val favSidecar = dir.resolve("IMG_1234.xmp")
            val rejSidecar = dir.resolve("IMG_5678.xmp")
            assertTrue(Files.exists(favSidecar))
            assertTrue(Files.exists(rejSidecar))
            assertFalse(Files.exists(dir.resolve("IMG_9999.xmp")))

            assertEquals(0, report.folded)
            assertEquals("5", parseDescription(favSidecar.readText()).getAttribute("xmp:Rating"))
            assertEquals("-1", parseDescription(rejSidecar.readText()).getAttribute("xmp:Rating"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `same-basename photos fold into one sidecar with reject winning`() = runTest {
        val dir = createTempDirectory("xmp-collide")
        try {
            // A RAW+JPEG pair sharing a basename both resolve to IMG_1234.xmp: one rejected, one
            // favourited. The fold must not clobber last-writer-wins — reject wins, and the merge
            // surfaces as folded == 1.
            val raw = photo(dir, "IMG_1234.CR2", "raw")
            val jpeg = photo(dir, "IMG_1234.JPG", "jpeg")

            val report = XmpSidecarPhotoExporter().exportSidecars(
                root = RootFolder(dir),
                photos = listOf(jpeg, raw),
                favouriteIds = setOf(PhotoId("jpeg")),
                rejectedIds = setOf(PhotoId("raw")),
                onProgress = { _, _ -> },
            )

            assertEquals(1, report.written)
            assertEquals(1, report.folded)
            assertEquals(0, report.skipped)
            assertTrue(report.failed.isEmpty())

            val sidecar = dir.resolve("IMG_1234.xmp")
            assertTrue(Files.exists(sidecar))
            val desc = parseDescription(sidecar.readText())
            assertEquals("-1", desc.getAttribute("xmp:Rating"))
            assertEquals("Rejected", desc.getAttribute("xmp:Label"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun photo(dir: Path, name: String, id: String): Photo {
        val path = dir.resolve(name)
        Files.write(path, byteArrayOf(1, 2, 3))
        return Photo(
            id = PhotoId(id),
            absolutePath = path,
            relativePath = name,
            fileName = name,
            sizeBytes = 3,
            lastModifiedEpochMs = 0,
        )
    }

    private fun parseDescription(packet: String): Element {
        val doc: Document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(packet.toByteArray(StandardCharsets.UTF_8)))
        val nodes = doc.getElementsByTagName("rdf:Description")
        assertEquals(1, nodes.length, "expected exactly one rdf:Description")
        return nodes.item(0) as Element
    }
}
