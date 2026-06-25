package com.vishalgupta.photoselector.data.prefs

import com.vishalgupta.photoselector.data.io.AtomicJsonWriter
import com.vishalgupta.photoselector.domain.repository.AppPreferencesRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * File-backed [AppPreferencesRepository]: a single small JSON document, written atomically through the
 * shared [AtomicJsonWriter] (the same writer categories and browse-position use — no new persistence
 * mechanism). Reads are memoised; writes are best-effort (a read-only location just means the flag
 * doesn't survive the session, which at worst re-shows a one-time coachmark or re-rolls the rollout
 * bucket — harmless).
 */
class JsonAppPreferences(
    private val file: Path,
    private val json: Json,
) : AppPreferencesRepository {

    @Volatile private var cached: PrefsDto? = null

    override fun hasSeenSimilarityCoachmark(): Boolean = load().seenSimilarityCoachmark

    override fun markSimilarityCoachmarkSeen() = mutate { it.copy(seenSimilarityCoachmark = true) }

    override fun isAutoUpdateCheckEnabled(): Boolean = load().autoUpdateCheckEnabled

    override fun setAutoUpdateCheckEnabled(enabled: Boolean) = mutate { it.copy(autoUpdateCheckEnabled = enabled) }

    override fun skippedUpdateVersion(): String? = load().skippedUpdateVersion

    override fun setSkippedUpdateVersion(version: String?) = mutate { it.copy(skippedUpdateVersion = version) }

    override fun installId(): String {
        load().installId?.let { return it }
        val generated = UUID.randomUUID().toString()
        mutate { it.copy(installId = generated) }
        return generated
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

    /** Apply [transform] to the current prefs, update the in-memory cache, and best-effort persist. */
    private fun mutate(transform: (PrefsDto) -> PrefsDto) {
        val updated = transform(load())
        cached = updated
        try {
            Files.createDirectories(file.parent)
            AtomicJsonWriter.write(file, json.encodeToString(PrefsDto.serializer(), updated).toByteArray(Charsets.UTF_8))
        } catch (_: Throwable) {
            // Best-effort; the in-memory cache still keeps this session coherent.
        }
    }

    @Serializable
    private data class PrefsDto(
        val seenSimilarityCoachmark: Boolean = false,
        val autoUpdateCheckEnabled: Boolean = true,
        val skippedUpdateVersion: String? = null,
        val installId: String? = null,
    )
}
