package com.vishalgupta.photoselector.data.filesystem

import com.vishalgupta.photoselector.data.favourites.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files

@Serializable
data class IndexEntryDto(
    val relPath: String,
    val size: Long,
    val mtimeMs: Long,
)

@Serializable
data class IndexDto(
    val version: Int = 1,
    val scannedAtMs: Long = 0L,
    val entries: List<IndexEntryDto> = emptyList(),
)

class IndexPersistence(private val json: Json) {

    fun read(root: RootFolder): IndexDto? {
        val file = root.indexFile
        if (!Files.exists(file)) return null
        return try {
            val text = Files.readString(file)
            json.decodeFromString(IndexDto.serializer(), text)
        } catch (_: Throwable) {
            null
        }
    }

    fun write(root: RootFolder, entries: List<IndexEntryDto>, scannedAtMs: Long) {
        if (!Files.isWritable(root.path)) return
        val dto = IndexDto(
            scannedAtMs = scannedAtMs,
            entries = entries.sortedBy { it.relPath },
        )
        val bytes = json.encodeToString(IndexDto.serializer(), dto).toByteArray(Charsets.UTF_8)
        try {
            AtomicJsonWriter.write(root.indexFile, bytes)
        } catch (_: Throwable) {
            // read-only or other I/O failure — silently skip
        }
    }

    fun rebuildPhotos(root: RootFolder, dto: IndexDto): List<Photo> {
        val photos = ArrayList<Photo>(dto.entries.size)
        for (entry in dto.entries) {
            photos += Photo(
                id = PhotoId(entry.relPath),
                absolutePath = root.path.resolve(entry.relPath),
                relativePath = entry.relPath,
                fileName = entry.relPath.substringAfterLast('/'),
                sizeBytes = entry.size,
                lastModifiedEpochMs = entry.mtimeMs,
            )
        }
        photos.sortBy { it.relativePath }
        return photos
    }
}
