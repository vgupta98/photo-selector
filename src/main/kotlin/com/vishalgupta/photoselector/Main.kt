package com.vishalgupta.photoselector

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.vishalgupta.photoselector.di.AppContainer
import com.vishalgupta.photoselector.presentation.common.PlatformLabels

fun main() = application {
    val container = remember { AppContainer() }
    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photo Selector",
        state = windowState,
        icon = painterResource("icon/app-icon.png"),
    ) {
        val photoPath by container.currentPhotoPath.collectAsState()
        val actions = container.systemActions
        MenuBar {
            Menu("File") {
                Item(
                    PlatformLabels.revealInFileManager,
                    enabled = photoPath != null,
                    shortcut = KeyShortcut(Key.R, meta = true),
                    onClick = { photoPath?.let { actions.revealInFileManager(it) } },
                )
                Item(
                    PlatformLabels.OPEN_WITH_DEFAULT_APP,
                    enabled = photoPath != null,
                    shortcut = KeyShortcut(Key.O, meta = true),
                    onClick = { photoPath?.let { actions.openWithDefaultApp(it) } },
                )
                Item(
                    PlatformLabels.preview,
                    enabled = photoPath != null,
                    shortcut = KeyShortcut(Key.Y, meta = true),
                    onClick = { photoPath?.let { actions.preview(it) } },
                )
            }
        }
        App(container)
    }
}
