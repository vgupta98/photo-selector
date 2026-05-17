package com.vishalgupta.photoselector.presentation.navigation

import com.vishalgupta.photoselector.domain.model.RootFolder

sealed interface Screen {
    data object RootPicker : Screen
    data class Browser(val root: RootFolder, val initialIndex: Int = 0) : Screen
    data class Favourites(val root: RootFolder, val returnIndex: Int = 0) : Screen
}
