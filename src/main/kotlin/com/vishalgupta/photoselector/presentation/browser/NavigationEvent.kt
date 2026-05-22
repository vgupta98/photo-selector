package com.vishalgupta.photoselector.presentation.browser

sealed interface NavigationEvent {
    data object AllDecided : NavigationEvent
}
