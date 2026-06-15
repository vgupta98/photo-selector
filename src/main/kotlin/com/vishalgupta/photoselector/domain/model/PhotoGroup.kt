package com.vishalgupta.photoselector.domain.model

/**
 * A scan-derived grouping of adjacent photos.
 *
 * A [Burst] collapses several near-simultaneous frames of one moment into a
 * single grid decision; a [Single] is the degenerate one-photo group so
 * callers can iterate a `List<PhotoGroup>` uniformly without special-casing.
 *
 * Grouping is a derived view computed once after scan (see `BurstGrouper`),
 * never persisted — recompute is cheap and the heuristic may change.
 */
sealed interface PhotoGroup {

    /** The frames in this group, in scan order. Always non-empty. */
    val photos: List<Photo>

    /** The frame shown as the group's representative in a collapsed grid tile. */
    val keyPhoto: Photo

    /**
     * Stable identity across re-scans, anchored to the first frame's id. Used to
     * track which groups the user has expanded so that state survives a re-scan
     * (per the burst-grouping brief).
     */
    val groupId: PhotoId get() = photos.first().id

    data class Single(val photo: Photo) : PhotoGroup {
        override val photos: List<Photo> = listOf(photo)
        override val keyPhoto: Photo = photo
    }

    /**
     * @param keyIndex which frame represents the burst on a collapsed tile. Defaults to the
     * middle frame: time-based [BurstGrouper] has no quality signal to pick a "best"
     * (favourites/categories are the model, not stars), so the middle is the neutral
     * representative. Similarity grouping passes the *sharpest* frame's index here, so the collapsed
     * tile's cover is the suggested keeper.
     */
    data class Burst(
        override val photos: List<Photo>,
        val keyIndex: Int = photos.size / 2,
    ) : PhotoGroup {
        init {
            require(photos.size >= 2) { "A burst needs at least two frames, got ${photos.size}" }
            require(keyIndex in photos.indices) { "keyIndex $keyIndex out of bounds for ${photos.size} frames" }
        }

        override val keyPhoto: Photo = photos[keyIndex]
    }
}
