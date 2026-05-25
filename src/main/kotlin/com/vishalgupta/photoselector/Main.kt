package com.vishalgupta.photoselector

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vishalgupta.photoselector.di.AppContainer
import com.vishalgupta.photoselector.presentation.common.SystemActions

fun main() = application {
    val container = remember { AppContainer() }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photo Selector",
        state = windowState,
    ) {
        val photoPath by container.currentPhotoPath.collectAsState()
        MenuBar {
            Menu("File") {
                Item(
                    "Reveal in Finder",
                    enabled = photoPath != null,
                    shortcut = KeyShortcut(Key.R, meta = true),
                    onClick = { photoPath?.let { SystemActions.revealInFinder(it) } },
                )
                Item(
                    "Open with Default App",
                    enabled = photoPath != null,
                    shortcut = KeyShortcut(Key.O, meta = true),
                    onClick = { photoPath?.let { SystemActions.openWithDefault(it) } },
                )
                Item(
                    "Quick Look",
                    enabled = photoPath != null,
                    shortcut = KeyShortcut(Key.Y, meta = true),
                    onClick = { photoPath?.let { SystemActions.quickLook(it) } },
                )
            }
        }
        App(container)
    }
}
