package com.vishalgupta.photoselector.domain.update

/** Outcome of an update check. The UI shows a banner only for [Available]; the other two are silent. */
sealed interface UpdateStatus {
    /** Already on the latest eligible build (or held out of the current rollout wave). */
    data object UpToDate : UpdateStatus

    /** The check itself couldn't run (offline, feed missing/malformed) — treated as "nothing to offer". */
    data object Unknown : UpdateStatus

    /** A newer build this install is eligible for. [mandatory] means it can't be skipped (a floor/critical release). */
    data class Available(
        val version: AppVersion,
        val downloadUrl: String,
        val notesUrl: String?,
        val mandatory: Boolean,
    ) : UpdateStatus
}
