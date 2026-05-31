package com.vishalgupta.photoselector.data.favourites

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A persisted favourite: the photo's root-relative path plus cheap identity hints
 * ([size], [mtimeMs]) used to re-attach the favourite after a folder rename or move.
 * A negative [size]/[mtimeMs] is a sentinel for "path-only" (a legacy v1 entry, or a
 * favourite whose photo wasn't in the last scan) — such entries match by exact path only.
 */
@Serializable
data class PhotoEntryDto(
    val path: String,
    val size: Long = -1,
    val mtimeMs: Long = -1,
)

/**
 * Legacy v1 schema: bare path strings. Also matches the original fieldless
 * `{ "favourites": [...] }` form, since a missing `version` defaults to 1.
 */
@Serializable
private data class FavouritesV1Dto(
    val version: Int = 1,
    val favourites: List<String> = emptyList(),
)

/** v2 schema: descriptor objects carrying (size, mtime) for rename-survival. */
@Serializable
data class FavouritesV2Dto(
    val version: Int = 2,
    val favourites: List<PhotoEntryDto> = emptyList(),
)

/**
 * Single source of truth for decoding/encoding the favourites file across schema
 * versions. Reads peek the `version` field (absent => 1) and dispatch to the matching
 * concrete DTO; a v1 file yields path-only descriptors (size/mtime = -1). Writes always
 * emit v2, so the next read resolves directly by path with no fallback cost.
 */
object FavouritesFile {
    fun decode(json: Json, text: String): List<PhotoEntryDto> {
        val version = json.parseToJsonElement(text)
            .jsonObject["version"]?.jsonPrimitive?.intOrNull ?: 1
        return when (version) {
            1 -> json.decodeFromString(FavouritesV1Dto.serializer(), text)
                .favourites.map { PhotoEntryDto(path = it) }
            else -> json.decodeFromString(FavouritesV2Dto.serializer(), text).favourites
        }
    }

    fun encode(json: Json, entries: List<PhotoEntryDto>): ByteArray {
        val dto = FavouritesV2Dto(favourites = entries.sortedBy { it.path })
        return json.encodeToString(FavouritesV2Dto.serializer(), dto).toByteArray(Charsets.UTF_8)
    }
}
