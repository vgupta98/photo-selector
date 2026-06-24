package com.vishalgupta.photoselector.data.update

import com.vishalgupta.photoselector.domain.update.AppVersion
import com.vishalgupta.photoselector.domain.update.UpdateManifest
import kotlinx.serialization.Serializable

/**
 * On-the-wire shape of the hosted `update-manifest.json`. Kept separate from the domain [UpdateManifest]
 * so the feed format can carry strings/optionals the domain doesn't (and so a forward-compatible field
 * the app doesn't know yet is just ignored — the shared Json is lenient). Policy fields default to a
 * full, non-mandatory rollout, so a minimal `{ latest, dmgUrl }` feed is valid.
 */
@Serializable
data class UpdateManifestDto(
    val latest: String,
    val dmgUrl: String,
    val notesUrl: String? = null,
    val minimumVersion: String? = null,
    val minOS: String? = null,
    val rollout: Double = 1.0,
    val mandatory: Boolean = false,
) {
    /** Validated domain view, or null if the feed is unusable (unparseable version, no download URL). */
    fun toDomainOrNull(): UpdateManifest? {
        val latestVersion = AppVersion.parseOrNull(latest) ?: return null
        if (dmgUrl.isBlank()) return null
        return UpdateManifest(
            latest = latestVersion,
            downloadUrl = dmgUrl,
            notesUrl = notesUrl?.takeIf { it.isNotBlank() },
            minimumVersion = minimumVersion?.let(AppVersion::parseOrNull),
            minOs = minOS?.let(AppVersion::parseOrNull),
            rollout = rollout.coerceIn(0.0, 1.0),
            mandatory = mandatory,
        )
    }
}
