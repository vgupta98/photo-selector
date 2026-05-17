package com.vishalgupta.photoselector.domain.model

import java.nio.file.Path

data class RootFolder(val path: Path) {
    val favouritesFile: Path get() = path.resolve(FAVOURITES_FILE_NAME)

    companion object {
        const val FAVOURITES_FILE_NAME: String = ".photo-selector-favourites.json"
    }
}
