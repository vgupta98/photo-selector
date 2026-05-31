package com.vishalgupta.photoselector.domain.model

/** Stable identifier for a [Category] within a root. The built-in Favourites category
 *  has the fixed id [Category.FAVOURITES_ID]; custom categories get an opaque generated id. */
@JvmInline
value class CategoryId(val value: String)
