package com.vishalgupta.photoselector.data.browse

import com.vishalgupta.photoselector.data.favourites.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.BrowsePositionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files

@Serializable
private data class BrowsePositionDto(
    val version: Int = 1,
    val lastIndex: Int = 0,
)

class JsonBrowsePositionRepository(
    private val json: Json,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : BrowsePositionRepository {

    private var cachedRoot: RootFolder? = null
    private var cachedIndex: Int = 0
    private var flushJob: Job? = null

    override fun save(root: RootFolder, index: Int) {
        cachedRoot = root
        cachedIndex = index
        scheduleFlush(root, index)
    }

    override fun load(root: RootFolder): Int {
        if (cachedRoot?.path == root.path) return cachedIndex
        val index = readFromDisk(root)
        cachedRoot = root
        cachedIndex = index
        return index
    }

    private fun scheduleFlush(root: RootFolder, index: Int) {
        flushJob?.cancel()
        flushJob = ioScope.launch {
            delay(500)
            writeToDisk(root, index)
        }
    }

    private fun readFromDisk(root: RootFolder): Int {
        val file = root.positionFile
        if (!Files.exists(file)) return 0
        return try {
            val text = Files.readString(file)
            json.decodeFromString(BrowsePositionDto.serializer(), text).lastIndex
        } catch (_: Throwable) {
            0
        }
    }

    private fun writeToDisk(root: RootFolder, index: Int) {
        val dto = BrowsePositionDto(lastIndex = index)
        val bytes = json.encodeToString(BrowsePositionDto.serializer(), dto).toByteArray(Charsets.UTF_8)
        try {
            AtomicJsonWriter.write(root.positionFile, bytes)
        } catch (_: Throwable) {
            // Read-only folder; position is still cached in memory.
        }
    }
}
