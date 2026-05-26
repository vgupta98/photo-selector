package com.vishalgupta.photoselector.domain.model

import java.nio.file.Path

data class RootFolder(val path: Path) {
    val favouritesFile: Path get() = path.resolve(FAVOURITES_FILE_NAME)
    val positionFile: Path get() = path.resolve(POSITION_FILE_NAME)
    val indexFile: Path get() = path.resolve(INDEX_FILE_NAME)

    companion object {
        const val FAVOURITES_FILE_NAME: String = ".photo-selector-favourites.json"
        const val POSITION_FILE_NAME: String = ".photo-selector-position.json"
        const val INDEX_FILE_NAME: String = ".photo-selector-index.json"
    }
}
