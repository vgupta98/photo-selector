package com.vishalgupta.photoselector.domain.update

/**
 * A dotted numeric version (`major.minor.patch`), the only thing this app needs to compare to decide
 * whether a newer build exists. Tolerant on parse so it doubles as the OS-version comparator: a missing
 * component reads as 0 (`"14.5"` -> 14.5.0) and any pre-release suffix is dropped (`"1.7.0-beta"` -> 1.7.0),
 * so a hand-written manifest or a `os.version` string both parse without the caller special-casing them.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** Parses `major[.minor[.patch]]` (extra components and any `-suffix`/`+suffix` ignored), or null if it has no leading number. */
        fun parseOrNull(raw: String): AppVersion? {
            val core = raw.trim().substringBefore('-').substringBefore('+')
            if (core.isEmpty()) return null
            val parts = core.split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            return AppVersion(
                major = major,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * Maps a stable per-install id to a fixed point in `[0.0, 1.0)` for staged rollout. Deterministic
 * (`String.hashCode` is spec-defined, so the same id always lands in the same place), so an install
 * stays on one side of the manifest's rollout cut across checks instead of flickering in and out of
 * the wave. Computed entirely on-device — the id is never transmitted — which keeps the "no telemetry"
 * promise intact while still supporting a server-staged rollout. Lives here, beside [AppVersion] and
 * its test, because it's pure version-adjacent math the use case merely consumes.
 */
fun rolloutBucket(installId: String): Double {
    val unsigned = installId.hashCode().toLong() and 0xFFFFFFFFL
    return unsigned.toDouble() / 0x1_0000_0000L
}
