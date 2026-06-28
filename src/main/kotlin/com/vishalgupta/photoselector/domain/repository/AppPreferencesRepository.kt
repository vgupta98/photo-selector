package com.vishalgupta.photoselector.domain.repository

/**
 * Small, global (not per-root) app preferences — the handful of one-off "remember this across
 * launches" flags that don't belong to a folder: the first-run Similarity coachmark, and the
 * update-checker's settings (opt-out, skipped version, and the install's stable rollout id). Kept
 * behind an interface so the persistence (and a no-op for tests) stays swappable.
 */
interface AppPreferencesRepository {
    /** True once the user has dismissed the first-run Similarity-lens coachmark; it then never reappears. */
    fun hasSeenSimilarityCoachmark(): Boolean

    /** Records that the Similarity coachmark was dismissed, so later launches don't show it again. */
    fun markSimilarityCoachmarkSeen()

    /** Whether the app may check for updates on launch. Defaults true; the user can turn it off. */
    fun isAutoUpdateCheckEnabled(): Boolean
    fun setAutoUpdateCheckEnabled(enabled: Boolean)

    /** The update version the user chose to skip ("don't remind me again"), or null. */
    fun skippedUpdateVersion(): String?
    fun setSkippedUpdateVersion(version: String?)

    /**
     * A stable random id for this install, generated once on first read and persisted. Used only
     * locally to place the install in a staged-rollout wave; it is never transmitted.
     */
    fun installId(): String
}
