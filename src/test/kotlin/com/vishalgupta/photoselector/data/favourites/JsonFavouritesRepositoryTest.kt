package com.vishalgupta.photoselector.data.favourites

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class JsonFavouritesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun photo(relative: String, size: Long, mtime: Long) = Photo(
        id = PhotoId(relative),
        absolutePath = tmp.root.toPath().resolve(relative),
        relativePath = relative,
        fileName = relative.substringAfterLast('/'),
        sizeBytes = size,
        lastModifiedEpochMs = mtime,
    )

    private fun repo(scanned: List<Photo>): Pair<JsonFavouritesRepository, RootFolder> {
        val root = RootFolder(tmp.root.toPath())
        return JsonFavouritesRepository(json) { scanned } to root
    }

    private fun writeFavouritesFile(root: RootFolder, content: String) {
        Files.writeString(root.favouritesFile, content)
    }

    private fun readFavouritesFile(root: RootFolder): String = Files.readString(root.favouritesFile)

    @Test
    fun v1FieldlessFile_loads() {
        val (repo, root) = repo(listOf(photo("a.jpg", 100, 1)))
        writeFavouritesFile(root, """{"favourites":["a.jpg"]}""")

        assertEquals(setOf(PhotoId("a.jpg")), repo.observe(root).value)
    }

    @Test
    fun v1FileWithVersionField_loads() {
        val (repo, root) = repo(listOf(photo("a.jpg", 100, 1)))
        writeFavouritesFile(root, """{"version":1,"favourites":["a.jpg"]}""")

        assertEquals(setOf(PhotoId("a.jpg")), repo.observe(root).value)
    }

    @Test
    fun v2File_roundTripsAndSurvivesFolderRename() {
        // Stored under the old folder name; the scan now sees the renamed folder.
        val (repo, root) = repo(listOf(photo("01-Ceremony/a.jpg", 100, 1700)))
        writeFavouritesFile(
            root,
            """{"version":2,"favourites":[{"path":"Ceremony/a.jpg","size":100,"mtimeMs":1700}]}""",
        )

        assertEquals(setOf(PhotoId("01-Ceremony/a.jpg")), repo.observe(root).value)
    }

    @Test
    fun toggle_emitsV2DescriptorWithSizeAndMtime() = runTest {
        val (repo, root) = repo(listOf(photo("a.jpg", 4823901, 1730812401000)))

        repo.toggle(root, PhotoId("a.jpg"))

        val written = readFavouritesFile(root)
        assertTrue("expected v2 version field, got: $written", written.contains("\"version\": 2"))
        assertTrue("expected size hint, got: $written", written.contains("\"size\": 4823901"))
        assertTrue("expected mtime hint, got: $written", written.contains("\"mtimeMs\": 1730812401000"))
    }

    @Test
    fun loadingV1ThenToggling_rewritesAsV2() = runTest {
        val (repo, root) = repo(listOf(photo("a.jpg", 100, 5), photo("b.jpg", 200, 6)))
        writeFavouritesFile(root, """{"favourites":["a.jpg"]}""")

        // Bind reads v1; toggling b adds it and rewrites the whole file as v2.
        repo.toggle(root, PhotoId("b.jpg"))

        val entries = FavouritesFile.decode(json, readFavouritesFile(root))
        assertEquals(
            setOf(PhotoEntryDto("a.jpg", 100, 5), PhotoEntryDto("b.jpg", 200, 6)),
            entries.toSet(),
        )
    }

    @Test
    fun observingBeforeScanIsSet_doesNotStickToEmpty() {
        // Simulates the scan result arriving after the first observe(): binding against an
        // empty scan must not cache the root as bound, or the persisted favourites would
        // silently vanish until a folder switch.
        var scanned = emptyList<Photo>()
        val root = RootFolder(tmp.root.toPath())
        val repo = JsonFavouritesRepository(json) { scanned }
        writeFavouritesFile(root, """{"version":2,"favourites":[{"path":"a.jpg","size":100,"mtimeMs":5}]}""")

        // Scan not ready yet: resolves to empty, but the bind must not stick.
        assertEquals(emptySet<PhotoId>(), repo.observe(root).value)

        // Scan completes; the next observe re-binds and recovers the favourite.
        scanned = listOf(photo("a.jpg", 100, 5))
        assertEquals(setOf(PhotoId("a.jpg")), repo.observe(root).value)
    }

    @Test
    fun untoggling_removesFromPersistedFile() = runTest {
        val (repo, root) = repo(listOf(photo("a.jpg", 100, 5)))
        repo.toggle(root, PhotoId("a.jpg")) // add
        repo.toggle(root, PhotoId("a.jpg")) // remove

        assertEquals(emptyList<PhotoEntryDto>(), FavouritesFile.decode(json, readFavouritesFile(root)))
        assertEquals(emptySet<PhotoId>(), repo.observe(root).value)
    }
}
