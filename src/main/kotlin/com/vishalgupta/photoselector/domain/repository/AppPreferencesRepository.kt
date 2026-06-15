package com.vishalgupta.photoselector.domain.repository

/**
 * Small, global (not per-root) app preferences — the handful of one-off "remember this across
 * launches" flags that don't belong to a folder. Today that is just whether the first-run Similarity
 * coachmark has been dismissed; kept behind an interface so the persistence (and a no-op for tests)
 * stays swappable.
 */
interface AppPreferencesRepository {
    /** True once the user has dismissed the first-run Similarity-lens coachmark; it then never reappears. */
    fun hasSeenSimilarityCoachmark(): Boolean

    /** Records that the Similarity coachmark was dismissed, so later launches don't show it again. */
    fun markSimilarityCoachmarkSeen()
}
