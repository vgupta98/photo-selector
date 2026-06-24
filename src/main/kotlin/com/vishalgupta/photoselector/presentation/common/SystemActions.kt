package com.vishalgupta.photoselector.presentation.common

import java.nio.file.Path

interface SystemActions {
    fun revealInFileManager(path: Path)
    fun openWithDefaultApp(path: Path)

    /** Open a URL in the user's default browser (e.g. a release download or notes page). */
    fun openUrl(url: String)
}
