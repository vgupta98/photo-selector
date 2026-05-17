package com.vishalgupta.photoselector.data.favourites

import kotlinx.serialization.Serializable

@Serializable
data class FavouritesDto(
    val version: Int = 1,
    val favourites: List<String> = emptyList(),
)
