package com.vishalgupta.photoselector

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vishalgupta.photoselector.di.AppContainer

fun main() = application {
    val container = remember { AppContainer() }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photo Selector",
        state = windowState,
    ) {
        App(container)
    }
}
