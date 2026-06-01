package com.vishalgupta.photoselector.data.browse

import com.vishalgupta.photoselector.data.io.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.BrowsePosition
import com.vishalgupta.photoselector.domain.repository.BrowsePositionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files

@Serializable
private data class BrowsePositionDto(
    // Written for forward-compat but never read (decode ignores it). lastPhotoId was
    // added since v1 as an optional field, which is backward-compatible — old files
    // decode fine — so no version bump was warranted; the stamp stays 1.
    val version: Int = 1,
    val lastIndex: Int = 0,
    val lastPhotoId: String? = null,
)

class JsonBrowsePositionRepository(
    private val json: Json,
) : BrowsePositionRepository {

    // @Volatile because load() is non-suspend and reads these from the main
    // thread, while writers update them on appScope under writeMutex — without
    // the volatile, there's no happens-before between writer and reader and
    // load() could see a torn snapshot.
    @Volatile private var cachedRoot: RootFolder? = null
    @Volatile private var cachedPosition: BrowsePosition = BrowsePosition()
    // Serializes load-modify-write so concurrent saveIndex / saveLastPhotoId
    // launched on appScope can't read the same `existing` snapshot and overwrite
    // each other's field.
    private val writeMutex = Mutex()

    override suspend fun save(root: RootFolder, position: BrowsePosition) {
        writeMutex.withLock {
            cachedRoot = root
            cachedPosition = position
            withContext(Dispatchers.IO) {
                writeToDisk(root, position)
            }
        }
    }

    override suspend fun saveIndex(root: RootFolder, index: Int) {
        writeMutex.withLock {
            val existing = loadLocked(root)
            val updated = existing.copy(lastIndex = index)
            cachedRoot = root
            cachedPosition = updated
            withContext(Dispatchers.IO) {
                writeToDisk(root, updated)
            }
        }
    }

    override suspend fun saveLastPhotoId(root: RootFolder, photoId: PhotoId?) {
        writeMutex.withLock {
            val existing = loadLocked(root)
            val updated = existing.copy(lastPhotoId = photoId)
            cachedRoot = root
            cachedPosition = updated
            withContext(Dispatchers.IO) {
                writeToDisk(root, updated)
            }
        }
    }

    override fun load(root: RootFolder): BrowsePosition = loadLocked(root)

    private fun loadLocked(root: RootFolder): BrowsePosition {
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
