package com.vishalgupta.photoselector.data.favourites

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

class JsonFavouritesRepository(
    private val json: Json,
) : FavouritesRepository {

    private val mutex = Mutex()

    private var boundRoot: RootFolder? = null
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
            favourites.value = emptySet()
            readOnly.value = false
        }
    }

    private fun bind(root: RootFolder) {
        // Synchronous read on the calling thread is acceptable: small file, infrequent.
        boundRoot = root
        favourites.value = readFromDisk(root)
        readOnly.value = !Files.isWritable(root.path)
    }

    private fun readFromDisk(root: RootFolder): Set<PhotoId> {
        val file = root.favouritesFile
        if (!Files.exists(file)) return emptySet()
        return try {
            val text = Files.readString(file)
            val dto = json.decodeFromString(FavouritesDto.serializer(), text)
            dto.favourites.map(::PhotoId).toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private suspend fun writeToDisk(root: RootFolder, snapshot: Set<PhotoId>) {
        if (boundRoot?.path != root.path) return
        val dto = FavouritesDto(favourites = snapshot.map { it.value }.sorted())
        val bytes = json.encodeToString(FavouritesDto.serializer(), dto).toByteArray(Charsets.UTF_8)
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
