package com.vishalgupta.photoselector.presentation.common

/**
 * How the grid collapses related frames into one tile. A three-way, mutually-exclusive lens the
 * user picks from the toolbar; grouping is grid-only presentation over the flat photo list (see
 * `GridDisplayModel`), so switching modes never loses navigation state.
 *
 * Each non-[Off] mode maps to one `PhotoGrouper` in the view model. Default is [Time] (the shipped
 * burst behaviour); [Similarity] is the opt-in visual lens, kept gentle by never being the default.
 */
enum class GroupingMode(val label: String) {
    /** No grouping: one tile per photo, flat and chronological. */
    Off("Off"),

    /** Time + camera proximity (`BurstGrouper`): rapid-fire frames of one moment collapse. */
    Time("Bursts"),

    /** Visual similarity (`SimilarityPhotoGrouper`): near-identical shots collapse regardless of time. */
    Similarity("Similar"),
}
