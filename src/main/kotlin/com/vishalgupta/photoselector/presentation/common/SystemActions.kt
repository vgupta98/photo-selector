package com.vishalgupta.photoselector.presentation.common

import java.nio.file.Path

interface SystemActions {
    fun revealInFileManager(path: Path)
    fun openWithDefaultApp(path: Path)
    fun preview(path: Path)
}
