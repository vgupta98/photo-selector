package com.vishalgupta.photoselector.data.favourites

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files

/**
 * Persists favourites to `<root>/.photo-selector-favourites.json`.
 *
 * On bind, the persisted entries are resolved against the photos found by the current
 * scan (via [scannedPhotos]) so favourites re-attach after a folder rename or move — see
 * [FavouritesResolver]. The in-memory model stays a plain `Set<PhotoId>` of *current*
 * paths; writes serialise the v2 descriptor form (path + size + mtime).
 */
class JsonFavouritesRepository(
    private val json: Json,
    private val scannedPhotos: (RootFolder) -> List<Photo>,
) : FavouritesRepository {

    private val mutex = Mutex()

    private var boundRoot: RootFolder? = null
    private var photosById: Map<PhotoId, Photo> = emptyMap()
    private val favourites = MutableStateFlow<Set<PhotoId>>(emptySet())
    private val readOnly = MutableStateFlow(false)

    override fun observe(root: RootFolder): StateFlow<Set<PhotoId>> {
        if (boundRoot?.path != root.path) {
            bind(root)
        }
        return favourites.asStateFlow()
    }

    override fun isReadOnly(root: RootFolder): StateFlow<Boolean> {
        if (boundRoot?.path != root.path) {
            bind(root)
        }
        return readOnly.asStateFlow()
    }

    override suspend fun toggle(root: RootFolder, id: PhotoId): Boolean {
        if (boundRoot?.path != root.path) bind(root)
        return mutex.withLock {
            val current = favourites.value
            val nowFavourite = id !in current
            val updated = if (nowFavourite) current + id else current - id
            favourites.value = updated
            writeToDisk(root, updated)
            nowFavourite
        }
    }

    override suspend fun clearContext() {
        mutex.withLock {
            boundRoot = null
            photosById = emptyMap()
            favourites.value = emptySet()
            readOnly.value = false
        }
    }

    private fun bind(root: RootFolder) {
        // Synchronous read on the calling thread is acceptable: small file, infrequent.
        val scanned = scannedPhotos(root)
        val entries = readFromDisk(root)
        photosById = scanned.associateBy { it.id }
        favourites.value = FavouritesResolver.resolve(entries, scanned)
        readOnly.value = !Files.isWritable(root.path)
        // Only treat the root as bound once we've resolved against a populated scan.
        // Resolving a non-empty favourites file against an empty scan (scan results not
        // set yet, or photos temporarily unavailable) would silently drop favourites and
        // stick — leaving boundRoot null lets the next observe/toggle re-bind and recover.
        boundRoot = if (scanned.isNotEmpty()) root else null
    }

    private fun readFromDisk(root: RootFolder): List<PhotoEntryDto> {
        val file = root.favouritesFile
        if (!Files.exists(file)) return emptyList()
        return try {
            FavouritesFile.decode(json, Files.readString(file))
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun writeToDisk(root: RootFolder, snapshot: Set<PhotoId>) {
        if (boundRoot?.path != root.path) return
        val entries = snapshot.map { id ->
            val photo = photosById[id]
            if (photo != null) {
                PhotoEntryDto(photo.relativePath, photo.sizeBytes, photo.lastModifiedEpochMs)
            } else {
                // Defensive: every favourited id originates from the bound scan, so this
                // normally never fires. It only guards a desync between the cached scan
                // and the snapshot — not orphan re-attachment, which happens at bind.
                PhotoEntryDto(path = id.value)
            }
        }
        val bytes = FavouritesFile.encode(json, entries)
        try {
            withContext(Dispatchers.IO) {
                AtomicJsonWriter.write(root.favouritesFile, bytes)
            }
            readOnly.value = false
        } catch (_: Throwable) {
            readOnly.value = true
        }
    }
}
