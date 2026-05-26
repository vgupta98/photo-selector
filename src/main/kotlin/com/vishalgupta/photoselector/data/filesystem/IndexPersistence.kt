package com.vishalgupta.photoselector.data.filesystem

import com.vishalgupta.photoselector.data.favourites.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.TreeMap

@Serializable
data class IndexEntryDto(
    val relPath: String,
    val size: Long,
    val mtimeMs: Long,
)

@Serializable
data class IndexDto(
    val version: Int = 1,
    val entries: List<IndexEntryDto> = emptyList(),
)

class IndexPersistence(private val json: Json) {

    fun read(root: RootFolder): MutableMap<String, IndexEntryDto> {
        val file = root.indexFile
        if (!Files.exists(file)) return caseInsensitiveMap()
        return try {
            val text = Files.readString(file)
            val dto = json.decodeFromString(IndexDto.serializer(), text)
            val map = caseInsensitiveMap()
            for (entry in dto.entries) {
                map[entry.relPath] = entry
            }
            map
        } catch (_: Throwable) {
            caseInsensitiveMap()
        }
    }

    fun write(root: RootFolder, entries: List<IndexEntryDto>) {
        if (!Files.isWritable(root.path)) return
        val dto = IndexDto(entries = entries.sortedBy { it.relPath })
        val bytes = json.encodeToString(IndexDto.serializer(), dto).toByteArray(Charsets.UTF_8)
        try {
            AtomicJsonWriter.write(root.indexFile, bytes)
        } catch (_: Throwable) {
            // read-only or other I/O failure — silently skip
        }
    }

    private fun caseInsensitiveMap(): TreeMap<String, IndexEntryDto> =
        TreeMap(String.CASE_INSENSITIVE_ORDER)
}
