package com.vishalgupta.photoselector.presentation.common

/**
 * A membership toggle that just happened, surfaced as a one-shot confirmation pill.
 * Shared by the grid and the browser so the two screens describe the same action the
 * same way ("Favourited" / "Added to <category>") instead of drifting apart.
 */
data class CategoryToggle(val categoryName: String, val isFavourite: Boolean, val added: Boolean)
