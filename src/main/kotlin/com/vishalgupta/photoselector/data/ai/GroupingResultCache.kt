package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.data.io.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * A persistent, content-keyed on-disk cache of a *computed grouping* — the lightweight group
 * structure (frame ids + key frame per group), NOT pixels or embeddings (that is [EmbeddingCache]'s
 * job). It exists so re-entering the Similarity lens on an unchanged folder is instant rather than
 * re-running the minute-long model pass: the embedding cache already makes the *second* embed cheap,
 * but the grouping itself was recomputed every time.
 *
 * Modelled on [EmbeddingCache]: a SHA key, sharding, atomic writes, best-effort size-capped eviction
 * by mtime. The key folds in the producing model's id (a model swap that changes vectors must re-key,
 * exactly as for embeddings) and a fingerprint of the photo set — each frame's `path|size|mtime`, in
 * order — so a source edit, add, remove, reorder, model swap, or format bump all miss automatically.
 * The photo set IS the (root, scope) here, so the fingerprint subsumes scoping without threading
 * root/scope through the grouper; the adjacency rule keeps groups inside one folder regardless.
 *
 * A hit still verifies every stored id resolves against the live photos and reconstructs from THOSE
 * `Photo` objects; any mismatch (or a corrupt/forward-version file) is treated as a miss and recomputes,
 * so a stale grouping can never show.
 */
class GroupingResultCache(
    cacheDir: Path,
    private val json: Json,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val groupingsDir = cacheDir.resolve("groupings")

    fun startEviction(scope: CoroutineScope) {
        scope.launch { evict() }
    }

    /** The cache key for [photos] under [modelId]. Public so the decorator computes it once per pass. */
    fun keyFor(modelId: String, photos: List<Photo>): String {
        val fingerprint = buildString {
            append(modelId)
            append('\n')
            append(FORMAT_VERSION)
            for (p in photos) {
                append('\n')
                append(p.absolutePath)
                append('|')
                append(p.sizeBytes)
                append('|')
                append(p.lastModifiedEpochMs)
            }
        }
        return sha256Hex(fingerprint)
    }

    /**
     * The cached grouping for [key], reconstructed against [photos], or null on a miss / any mismatch.
     * [photos] is the live slice: the stored ids are resolved against it so the returned groups carry
     * the current [Photo] instances.
     */
    fun get(key: String, photos: List<Photo>): List<PhotoGroup>? {
        val file = cacheFileFor(key)
        if (!file.exists()) return null
        return try {
            val dto = json.decodeFromString(GroupingFileDto.serializer(), Files.readString(file))
            if (dto.version != FORMAT_VERSION) return null
            reconstruct(dto, photos)
        } catch (_: Throwable) {
            file.deleteIfExists()
            null
        }
    }

    fun put(key: String, groups: List<PhotoGroup>) {
        val dto = GroupingFileDto(
            version = FORMAT_VERSION,
            groups = groups.map { group ->
                when (group) {
                    is PhotoGroup.Single -> GroupDto(ids = listOf(group.photo.id.value))
                    is PhotoGroup.Burst -> GroupDto(
                        ids = group.photos.map { it.id.value },
                        keyIndex = group.keyIndex,
                        keyIsSuggested = group.keyIsSuggested,
                    )
                }
            },
        )
        try {
            AtomicJsonWriter.write(
                cacheFileFor(key),
                json.encodeToString(GroupingFileDto.serializer(), dto).toByteArray(Charsets.UTF_8),
            )
        } catch (_: Throwable) {
            // Non-fatal — next entry into the lens just recomputes.
        }
    }

    private fun reconstruct(dto: GroupingFileDto, photos: List<Photo>): List<PhotoGroup>? {
        val byId = photos.associateBy { it.id.value }
        return try {
            dto.groups.map { g ->
                val frames = g.ids.map { id -> byId[id] ?: return null }
                if (frames.size >= 2) {
                    PhotoGroup.Burst(frames, keyIndex = g.keyIndex, keyIsSuggested = g.keyIsSuggested)
                } else {
                    PhotoGroup.Single(frames.first())
                }
            }
        } catch (_: Throwable) {
            // A corrupt entry (e.g. an out-of-bounds keyIndex tripping Burst's require) — recompute.
            null
        }
    }

    private fun evict() {
        if (!groupingsDir.exists()) return
        try {
            data class CacheFile(val path: Path, val size: Long, val lastModified: Long)

            val files = groupingsDir.toFile().walkTopDown()
                .filter { it.isFile && it.extension == FILE_EXTENSION }
                .map { CacheFile(it.toPath(), it.length(), it.lastModified()) }
                .toMutableList()
            var remaining = files.sumOf { it.size }
            if (remaining <= maxBytes) return
            files.sortBy { it.lastModified }
            for (f in files) {
                if (remaining <= maxBytes) break
                f.path.deleteIfExists()
                remaining -= f.size
            }
        } catch (_: Throwable) {
            // Eviction is best-effort.
        }
    }

    private fun cacheFileFor(key: String): Path =
        groupingsDir.resolve(key.substring(0, 2)).resolve("$key.$FILE_EXTENSION")

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class GroupingFileDto(val version: Int, val groups: List<GroupDto>)

    @Serializable
    private data class GroupDto(
        val ids: List<String>,
        val keyIndex: Int = 0,
        val keyIsSuggested: Boolean = false,
    )

    companion object {
        // Bump when the on-disk schema (or the meaning of a stored field) changes.
        const val FORMAT_VERSION = 1
        const val DEFAULT_MAX_BYTES: Long = 64L * 1024 * 1024
        private const val FILE_EXTENSION = "grp"
    }
}
