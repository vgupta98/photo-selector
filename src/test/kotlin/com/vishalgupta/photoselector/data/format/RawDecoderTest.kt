package com.vishalgupta.photoselector.data.format

import com.sun.jna.Platform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Wiring + decode coverage for [RawDecoder]. The decode path delegates to the same [macos.MacImageIO]
 * bridge as [HeicDecoder], so the bridge itself (orientation, sRGB BGRA conversion, downscale) is
 * already exercised by the HEIC tests; here we pin the registration contract that makes RAW files
 * show up in scans, plus a fixture-gated real decode for anyone with a sample RAW on hand.
 *
 * No RAW fixture can be synthesised at test time (`sips` writes HEIC but not CR2/ARW/DNG), so the
 * decode test self-skips unless a real RAW is dropped at `build/raw-probe/fixture.<ext>`.
 */
class RawDecoderTest {

    private val format = RawDecoder().format

    @Test
    fun `registers every major RAW extension, lowercased`() {
        val registry = DefaultPhotoFormatRegistry(decoders = listOf(RawDecoder()))
        val expected = setOf("cr2", "cr3", "arw", "nef", "nrw", "raf", "dng", "rw2", "orf")

        expected.forEach { ext ->
            assertTrue("ext .$ext should be supported", registry.isSupported(Path.of("shot.$ext")))
            // Case-insensitive: the scanner sees whatever case the filesystem reports.
            assertTrue("ext .${ext.uppercase()} should be supported", registry.isSupported(Path.of("shot.${ext.uppercase()}")))
        }
        assertEquals(expected, format.extensions)
        assertEquals("raw", format.id)
    }

    @Test
    fun `platform support mirrors the ImageIO bridge availability`() {
        assertEquals(Platform.isMac(), RawDecoder.isSupportedOnThisPlatform())
    }

    @Test
    fun `does not claim formats owned by other decoders`() {
        // RAW must not poach the extensions JPEG/PNG/HEIC own, or the registry wiring would be ambiguous.
        listOf("jpg", "jpeg", "png", "heic", "heif").forEach {
            assertTrue(".$it is not a RAW extension", it !in format.extensions)
        }
    }

    @Test
    fun `decodes every real RAW fixture to oriented sRGB BGRA when present`() = runBlocking {
        assumeTrue("macOS-only RAW decode", Platform.isMac())
        val fixtures = locateFixtures()
        assumeTrue("drop sample RAW files in build/raw-probe/ to exercise this", fixtures.isNotEmpty())

        fixtures.forEach { fixture ->
            val decoded = RawDecoder().decode(fixture, targetMaxDimensionPx = 512)
            val name = fixture.fileName

            assertTrue("$name: width should be positive", decoded.width > 0)
            assertTrue("$name: height should be positive", decoded.height > 0)
            assertEquals("$name: buffer size matches dimensions", decoded.width * decoded.height * 4, decoded.bgraBytes.size)
            assertTrue("$name: longer edge capped to the 512px target", maxOf(decoded.width, decoded.height) <= 512)
            assertTrue("$name: decoded pixels should not be all zero", decoded.bgraBytes.any { it.toInt() != 0 })
        }
    }

    @Test
    fun `full-resolution request decodes a real RAW fixture when present`() = runBlocking {
        assumeTrue("macOS-only RAW decode", Platform.isMac())
        val fixture = locateFixtures().firstOrNull()
        assumeTrue("drop a sample RAW in build/raw-probe/ to exercise this", fixture != null)
        requireNotNull(fixture)

        // null target exercises the no-cap path that returns null for RAW unless the bridge supplies
        // a sentinel max — the bug a 512px-only test would miss.
        val decoded = RawDecoder().decode(fixture, targetMaxDimensionPx = null)

        assertTrue("full-res longer edge should exceed the 512 thumbnail size", maxOf(decoded.width, decoded.height) > 512)
        assertEquals(decoded.width * decoded.height * 4, decoded.bgraBytes.size)
    }

    /** Every file under build/raw-probe/ whose extension RawDecoder claims. */
    private fun locateFixtures(): List<Path> {
        val dir = Path.of("build/raw-probe")
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in format.extensions }
                .sorted()
                .toList()
        }
    }
}
