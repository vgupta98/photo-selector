package com.vishalgupta.photoselector.domain.update

/**
 * Decides whether *this* install should be offered the build the feed advertises. Pure policy over the
 * fetched [UpdateManifest]: newer-than-current, the OS floor, the staged-rollout wave, and the mandatory
 * floor. Knows nothing about how the user was notified or what they've skipped — that stays in the view
 * model — so the rules are trivially unit-testable.
 */
class CheckForUpdateUseCase(private val repository: UpdateRepository) {

    suspend operator fun invoke(
        current: AppVersion,
        osVersion: AppVersion?,
        rolloutBucket: Double,
    ): UpdateStatus {
        val manifest = repository.fetchManifest() ?: return UpdateStatus.Unknown
        if (manifest.latest <= current) return UpdateStatus.UpToDate

        // Don't offer a build this machine is too old to run. A null osVersion (os.version didn't
        // parse) skips the floor on purpose: a notifier should err toward informing the user, who can
        // judge for themselves, rather than silently hiding an update over an unreadable os.version —
        // which never happens on the supported macOS anyway. Locked by a unit test.
        if (manifest.minOs != null && osVersion != null && osVersion < manifest.minOs) {
            return UpdateStatus.UpToDate
        }

        // Below the hard floor: everyone must move, regardless of the rollout wave.
        val belowFloor = manifest.minimumVersion != null && current < manifest.minimumVersion

        // Staged rollout: only the leading fraction of installs is in the wave yet. The floor jumps the queue.
        if (!belowFloor && rolloutBucket >= manifest.rollout) return UpdateStatus.UpToDate

        return UpdateStatus.Available(
            version = manifest.latest,
            downloadUrl = manifest.downloadUrl,
            notesUrl = manifest.notesUrl,
            mandatory = manifest.mandatory || belowFloor,
        )
    }
}
