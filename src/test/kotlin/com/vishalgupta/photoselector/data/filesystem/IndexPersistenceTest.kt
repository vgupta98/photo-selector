package com.vishalgupta.photoselector.data.filesystem

import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexPersistenceTest {

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    private val persistence = IndexPersistence(json)

    @Test
    fun `read returns null when no index file exists`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            assertNull(persistence.read(root))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `write then read round-trips entries and timestamp`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            val entries = listOf(
                IndexEntryDto("Ceremony/IMG_001.jpg", 4823901, 1730812401000),
                IndexEntryDto("Reception/IMG_100.jpg", 3200000, 1730812500000),
            )
            val scannedAt = 1730900000000L
            persistence.write(root, entries, scannedAt)
            assertTrue(Files.exists(root.indexFile))

            val dto = persistence.read(root)
            assertNotNull(dto)
            assertEquals(2, dto.entries.size)
            assertEquals(scannedAt, dto.scannedAtMs)
            assertEquals("Ceremony/IMG_001.jpg", dto.entries[0].relPath)
            assertEquals(4823901, dto.entries[0].size)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `read returns null on malformed JSON`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            Files.writeString(root.indexFile, "not valid json {{{")
            assertNull(persistence.read(root))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `write skips on read-only directory`() {
        val dir = Files.createTempDirectory("index-test")
        val readOnlyDir = dir.resolve("readonly")
        Files.createDirectory(readOnlyDir)
        readOnlyDir.toFile().setWritable(false)
        try {
            val root = RootFolder(readOnlyDir)
            val entries = listOf(IndexEntryDto("a.jpg", 100, 1000))
            persistence.write(root, entries, System.currentTimeMillis())
            assertFalse(Files.exists(root.indexFile))
        } finally {
            readOnlyDir.toFile().setWritable(true)
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `entries are sorted by relPath on write`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            val entries = listOf(
                IndexEntryDto("z/last.jpg", 100, 1000),
                IndexEntryDto("a/first.jpg", 200, 2000),
            )
            persistence.write(root, entries, System.currentTimeMillis())

            val text = Files.readString(root.indexFile)
            val firstIdx = text.indexOf("a/first.jpg")
            val lastIdx = text.indexOf("z/last.jpg")
            assertTrue(firstIdx < lastIdx)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rebuildPhotos reconstructs Photo list from index`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            val dto = IndexDto(
                scannedAtMs = 1730900000000L,
                entries = listOf(
                    IndexEntryDto("b/second.jpg", 200, 2000),
                    IndexEntryDto("a/first.jpg", 100, 1000),
                ),
            )
            val photos = persistence.rebuildPhotos(root, dto)
            assertEquals(2, photos.size)
            assertEquals("a/first.jpg", photos[0].relativePath)
            assertEquals("b/second.jpg", photos[1].relativePath)
            assertEquals(100L, photos[0].sizeBytes)
            assertEquals(dir.resolve("a/first.jpg"), photos[0].absolutePath)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
