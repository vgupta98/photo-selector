package com.vishalgupta.photoselector.domain.model

/**
 * A flat, per-root bucket of photos. [builtIn] is true only for Favourites, which
 * always exists and cannot be renamed or deleted.
 *
 * Ship exactly these three fields. Future additions (colour, icon, a smart-filter
 * `kind`) are additive — they decode through `ignoreUnknownKeys` and construct via
 * named args — so do NOT overload [builtIn] or the `favourites` id as a "smartness"
 * signal; when `kind` arrives it is an orthogonal enum defaulting to MANUAL.
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val builtIn: Boolean,
) {
    companion object {
        /** Fixed id of the built-in Favourites category, stable across roots and versions. */
        val FAVOURITES_ID: CategoryId = CategoryId("favourites")
        const val FAVOURITES_NAME: String = "Favourites"

        fun favourites(): Category = Category(FAVOURITES_ID, FAVOURITES_NAME, builtIn = true)
    }
}
