package com.vishalgupta.photoselector.data.filesystem

import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexPersistenceTest {

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    private val persistence = IndexPersistence(json)

    @Test
    fun `read returns empty map when no index file exists`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            val result = persistence.read(root)
            assertTrue(result.isEmpty())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `write then read round-trips entries`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            val entries = listOf(
                IndexEntryDto("Ceremony/IMG_001.jpg", 4823901, 1730812401000),
                IndexEntryDto("Reception/IMG_100.jpg", 3200000, 1730812500000),
            )
            persistence.write(root, entries)
            assertTrue(Files.exists(root.indexFile))

            val loaded = persistence.read(root)
            assertEquals(2, loaded.size)
            assertEquals(4823901, loaded["Ceremony/IMG_001.jpg"]!!.size)
            assertEquals(3200000, loaded["Reception/IMG_100.jpg"]!!.size)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `read matches case-insensitively`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            val entries = listOf(
                IndexEntryDto("Ceremony/IMG_001.jpg", 4823901, 1730812401000),
            )
            persistence.write(root, entries)

            val loaded = persistence.read(root)
            val hit = loaded["ceremony/img_001.jpg"]
            assertEquals(4823901, hit!!.size)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `read returns empty map on malformed JSON`() {
        val dir = Files.createTempDirectory("index-test")
        try {
            val root = RootFolder(dir)
            Files.writeString(root.indexFile, "not valid json {{{")
            val result = persistence.read(root)
            assertTrue(result.isEmpty())
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
            persistence.write(root, entries)
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
            persistence.write(root, entries)

            val text = Files.readString(root.indexFile)
            val firstIdx = text.indexOf("a/first.jpg")
            val lastIdx = text.indexOf("z/last.jpg")
            assertTrue(firstIdx < lastIdx)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
