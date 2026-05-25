package com.vishalgupta.photoselector.presentation.common

import java.nio.file.Path

object SystemActions {
    fun revealInFinder(path: Path) {
        ProcessBuilder("open", "-R", path.toString()).start()
    }

    fun openWithDefault(path: Path) {
        ProcessBuilder("open", path.toString()).start()
    }

    fun quickLook(path: Path) {
        ProcessBuilder("qlmanage", "-p", path.toString()).start()
    }
}
