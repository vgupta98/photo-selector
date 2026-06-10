package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.data.io.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.model.Photo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

/**
 * A persistent, content-keyed, size-capped on-disk cache of per-photo [PhotoFeatures]. The exact
 * shape and discipline of [com.vishalgupta.photoselector.data.image.DiskThumbnailCache]: a
 * `(path|size|mtime|modelId|version)` SHA key (so a source edit, a model swap or a format bump all
 * miss automatically), 256-way sharding, atomic writes, and best-effort LRU eviction by mtime.
 *
 * Embedding a folder of photos is the feature's one expensive step; caching it is what lets the
 * cost be paid once and survive a restart (the brief's "cached to disk, survives a restart without
 * recompute" acceptance criterion). Entries are tiny (a few KB), so the cap is generous.
 *
 * [modelId] is the producing model's identity; it is folded into the key so two models never read
 * each other's vectors. Each entry is a small binary blob, not JSON — vectors are dense floats.
 */
class EmbeddingCache(
    cacheDir: Path,
    private val modelId: String,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val embeddingsDir = cacheDir.resolve("embeddings")

    fun startEviction(scope: CoroutineScope) {
        scope.launch { evict() }
    }

    fun get(photo: Photo): PhotoFeatures? {
        val file = cacheFileFor(photo)
        if (!file.exists()) return null
        return try {
            decode(Files.readAllBytes(file))
        } catch (_: Throwable) {
            file.deleteIfExists()
            null
        }
    }

    fun put(photo: Photo, features: PhotoFeatures) {
        try {
            AtomicJsonWriter.write(cacheFileFor(photo), encode(features))
        } catch (_: Throwable) {
            // Non-fatal — next session just re-embeds this photo.
        }
    }

    private fun encode(features: PhotoFeatures): ByteArray {
        val dims = features.embedding.size
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dims * Float.SIZE_BYTES)
        buffer.putInt(MAGIC)
        buffer.putInt(FORMAT_VERSION)
        buffer.putInt(dims)
        buffer.putFloat(features.sharpness)
        for (v in features.embedding) buffer.putFloat(v)
        return buffer.array()
    }

    private fun decode(bytes: ByteArray): PhotoFeatures? {
        if (bytes.size < HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.int != MAGIC) return null
        if (buffer.int != FORMAT_VERSION) return null
        val dims = buffer.int
        if (dims <= 0 || dims > MAX_DIMENSIONS) return null
        if (bytes.size != HEADER_BYTES + dims * Float.SIZE_BYTES) return null
        val sharpness = buffer.float
        val embedding = FloatArray(dims) { buffer.float }
        return PhotoFeatures(embedding = embedding, sharpness = sharpness)
    }

    private fun evict() {
        if (!embeddingsDir.exists()) return
        try {
            data class CacheFile(val path: Path, val size: Long, val lastModified: Long)

            val files = embeddingsDir.toFile().walkTopDown()
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

    private fun cacheFileFor(photo: Photo): Path {
        val input = "${photo.absolutePath}|${photo.sizeBytes}|${photo.lastModifiedEpochMs}|$modelId|$FORMAT_VERSION"
        val hash = sha256Hex(input)
        val shard = hash.substring(0, 2)
        return embeddingsDir.resolve(shard).resolve("$hash.$FILE_EXTENSION")
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_MAX_BYTES: Long = 256L * 1024 * 1024
        private const val MAGIC = 0x50534531 // "PSE1"
        private const val FILE_EXTENSION = "emb"
        private const val MAX_DIMENSIONS = 1 shl 16
        // magic + version + dims + sharpness
        private const val HEADER_BYTES = 4 * Int.SIZE_BYTES
    }
}
