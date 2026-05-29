package com.vishalgupta.photoselector.data.browse

import com.vishalgupta.photoselector.data.favourites.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.BrowsePosition
import com.vishalgupta.photoselector.domain.repository.BrowsePositionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files

@Serializable
private data class BrowsePositionDto(
    val version: Int = 2,
    val lastIndex: Int = 0,
    val lastPhotoId: String? = null,
)

class JsonBrowsePositionRepository(
    private val json: Json,
) : BrowsePositionRepository {

    private var cachedRoot: RootFolder? = null
    private var cachedPosition: BrowsePosition = BrowsePosition()

    override suspend fun save(root: RootFolder, position: BrowsePosition) {
        cachedRoot = root
        cachedPosition = position
        withContext(Dispatchers.IO) {
            writeToDisk(root, position)
        }
    }

    override suspend fun saveIndex(root: RootFolder, index: Int) {
        val existing = load(root)
        val updated = existing.copy(lastIndex = index)
        cachedRoot = root
        cachedPosition = updated
        withContext(Dispatchers.IO) {
            writeToDisk(root, updated)
        }
    }

    override suspend fun saveLastPhotoId(root: RootFolder, photoId: PhotoId?) {
        val existing = load(root)
        val updated = existing.copy(lastPhotoId = photoId)
        cachedRoot = root
        cachedPosition = updated
        withContext(Dispatchers.IO) {
            writeToDisk(root, updated)
        }
    }

    override fun load(root: RootFolder): BrowsePosition {
        if (cachedRoot?.path == root.path) return cachedPosition
        val position = readFromDisk(root)
        cachedRoot = root
        cachedPosition = position
        return position
    }

    private fun readFromDisk(root: RootFolder): BrowsePosition {
        val file = root.positionFile
        if (!Files.exists(file)) return BrowsePosition()
        return try {
            val text = Files.readString(file)
            val dto = json.decodeFromString(BrowsePositionDto.serializer(), text)
            BrowsePosition(
                lastIndex = dto.lastIndex,
                lastPhotoId = dto.lastPhotoId?.let { PhotoId(it) },
            )
        } catch (_: Throwable) {
            BrowsePosition()
        }
    }

    private fun writeToDisk(root: RootFolder, position: BrowsePosition) {
        val dto = BrowsePositionDto(
            lastIndex = position.lastIndex,
            lastPhotoId = position.lastPhotoId?.value,
        )
        val bytes = json.encodeToString(BrowsePositionDto.serializer(), dto).toByteArray(Charsets.UTF_8)
        try {
            AtomicJsonWriter.write(root.positionFile, bytes)
        } catch (_: Throwable) {
            // Read-only folder; position is still cached in memory.
        }
    }
}
