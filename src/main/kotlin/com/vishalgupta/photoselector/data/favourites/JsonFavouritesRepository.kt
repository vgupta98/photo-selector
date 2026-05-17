package com.vishalgupta.photoselector.data.favourites

import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files

class JsonFavouritesRepository(
    private val json: Json,
) : FavouritesRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var boundRoot: RootFolder? = null
    private val favourites = MutableStateFlow<Set<PhotoId>>(emptySet())
    private val readOnly = MutableStateFlow(false)
    private var pendingWrite: Job? = null

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
        val current = favourites.value
        val nowFavourite: Boolean
        val updated: Set<PhotoId> = if (id in current) {
            nowFavourite = false
            current - id
        } else {
            nowFavourite = true
            current + id
        }
        favourites.value = updated
        scheduleWrite(root)
        return nowFavourite
    }

    override suspend fun clearContext() {
        mutex.withLock {
            pendingWrite?.let { job ->
                if (job.isActive) job.join()
            }
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

    private fun scheduleWrite(root: RootFolder) {
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(DEBOUNCE_MS)
            mutex.withLock {
                if (boundRoot?.path != root.path) return@withLock
                val snapshot = favourites.value
                val dto = FavouritesDto(
                    favourites = snapshot.map { it.value }.sorted(),
                )
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
    }

    companion object {
        private const val DEBOUNCE_MS = 250L
    }
}
