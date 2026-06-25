package com.vishalgupta.photoselector.presentation.common

import java.nio.file.Path

class MacSystemActions : SystemActions {
    override fun revealInFileManager(path: Path) {
        ProcessBuilder("open", "-R", path.toString()).start()
    }

    override fun openWithDefaultApp(path: Path) {
        ProcessBuilder("open", path.toString()).start()
    }

    override fun openUrl(url: String) {
        ProcessBuilder("open", url).start()
    }
}
