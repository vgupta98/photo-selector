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

        // Don't offer a build this machine is too old to run.
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

/**
 * Maps a stable per-install id to a fixed point in `[0.0, 1.0)` for staged rollout. Deterministic
 * (`String.hashCode` is spec-defined, so the same id always lands in the same place), so an install
 * stays on one side of the [UpdateManifest.rollout] cut across checks instead of flickering in and out
 * of the wave. Computed entirely on-device — the id is never transmitted — which is what keeps the
 * "no telemetry" promise intact while still supporting a server-staged rollout.
 */
fun rolloutBucket(installId: String): Double {
    val unsigned = installId.hashCode().toLong() and 0xFFFFFFFFL
    return unsigned.toDouble() / 0x1_0000_0000L
}
