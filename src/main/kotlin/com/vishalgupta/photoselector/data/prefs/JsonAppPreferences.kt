package com.vishalgupta.photoselector.data.prefs

import com.vishalgupta.photoselector.data.io.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.repository.AppPreferencesRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * File-backed [AppPreferencesRepository]: a single small JSON document, written atomically through the
 * shared [AtomicJsonWriter] (the same writer categories and browse-position use — no new persistence
 * mechanism). Reads are memoised; writes are best-effort (a read-only location just means the flag
 * doesn't survive the session, which only re-shows a one-time coachmark — harmless).
 */
class JsonAppPreferences(
    private val file: Path,
    private val json: Json,
) : AppPreferencesRepository {

    @Volatile private var cached: PrefsDto? = null

    override fun hasSeenSimilarityCoachmark(): Boolean = load().seenSimilarityCoachmark

    override fun markSimilarityCoachmarkSeen() {
        val updated = load().copy(seenSimilarityCoachmark = true)
        cached = updated
        try {
            Files.createDirectories(file.parent)
            AtomicJsonWriter.write(file, json.encodeToString(PrefsDto.serializer(), updated).toByteArray(Charsets.UTF_8))
        } catch (_: Throwable) {
            // Best-effort; the in-memory cache still suppresses the coachmark for this session.
        }
    }

    private fun load(): PrefsDto = cached ?: readFromDisk().also { cached = it }

    private fun readFromDisk(): PrefsDto {
        if (!Files.exists(file)) return PrefsDto()
        return try {
            json.decodeFromString(PrefsDto.serializer(), Files.readString(file))
        } catch (_: Throwable) {
            PrefsDto()
        }
    }

    @Serializable
    private data class PrefsDto(val seenSimilarityCoachmark: Boolean = false)
}
