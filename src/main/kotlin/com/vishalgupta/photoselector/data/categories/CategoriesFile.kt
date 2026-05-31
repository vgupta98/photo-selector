package com.vishalgupta.photoselector.data.categories

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A persisted photo reference: the root-relative path plus cheap identity hints
 * ([size], [mtimeMs]) used to re-attach it after a folder rename or move. A negative
 * [size]/[mtimeMs] is a sentinel for "path-only" (a legacy v1 favourites entry, or a
 * photo not present in the last scan) — such entries match by exact path only.
 *
 * This is the shared descriptor across the favourites v2 schema and the categories v3
 * schema, so a category membership survives renames exactly like a favourite does.
 */
@Serializable
data class PhotoEntryDto(
    val path: String,
    val size: Long = -1,
    val mtimeMs: Long = -1,
)

/** A category as persisted in the v3 file: metadata plus its photo descriptors. */
@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val builtIn: Boolean = false,
    val photos: List<PhotoEntryDto> = emptyList(),
)

/**
 * v3 schema: N flat categories, each wrapping descriptor objects (NOT bare path
 * strings — bare strings would re-orphan memberships on every folder rename and undo
 * the stable-id work).
 */
@Serializable
private data class CategoriesV3Dto(
    val version: Int = 3,
    val categories: List<CategoryDto> = emptyList(),
)

/**
 * Decode/encode the categories file. Reads peek the `version` field and dispatch to
 * the matching concrete DTO; writes always emit v3.
 */
object CategoriesFile {
    fun decode(json: Json, text: String): List<CategoryDto> {
        // version is peeked for forward-compatibility; v3 is the only shape today.
        val version = json.parseToJsonElement(text)
            .jsonObject["version"]?.jsonPrimitive?.intOrNull ?: 3
        return when (version) {
            else -> json.decodeFromString(CategoriesV3Dto.serializer(), text).categories
        }
    }

    fun encode(json: Json, categories: List<CategoryDto>): ByteArray {
        val dto = CategoriesV3Dto(categories = categories)
        return json.encodeToString(CategoriesV3Dto.serializer(), dto).toByteArray(Charsets.UTF_8)
    }
}

/**
 * Legacy single-bucket favourites file, read once to migrate into the built-in
 * Favourites category. Handles the original fieldless `{ "favourites": [...] }` and
 * `{ "version": 1, ... }` (bare path strings) plus v2 (`{ "version": 2, favourites:
 * [descriptor] }`). A v1 file yields path-only descriptors (size/mtime = -1).
 */
object LegacyFavouritesFile {
    @Serializable
    private data class V1(val version: Int = 1, val favourites: List<String> = emptyList())

    @Serializable
    private data class V2(val version: Int = 2, val favourites: List<PhotoEntryDto> = emptyList())

    fun decode(json: Json, text: String): List<PhotoEntryDto> {
        val version = json.parseToJsonElement(text)
            .jsonObject["version"]?.jsonPrimitive?.intOrNull ?: 1
        return when (version) {
            1 -> json.decodeFromString(V1.serializer(), text).favourites.map { PhotoEntryDto(path = it) }
            else -> json.decodeFromString(V2.serializer(), text).favourites
        }
    }
}
