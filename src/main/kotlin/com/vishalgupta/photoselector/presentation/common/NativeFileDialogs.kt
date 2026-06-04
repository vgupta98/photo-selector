package com.vishalgupta.photoselector.presentation.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path

/**
 * Native OS file pickers via java.awt.FileDialog. The directory-selection toggle below
 * (`apple.awt.fileDialogForDirectories`) is macOS-specific; a Windows build will need its own
 * directory-picker path here.
 */
object NativeFileDialogs {

    suspend fun pickDirectory(title: String, parent: Frame? = null): Path? = withContext(Dispatchers.Swing) {
        val key = "apple.awt.fileDialogForDirectories"
        val previous = System.getProperty(key)
        System.setProperty(key, "true")
        try {
            val dialog = FileDialog(parent, title, FileDialog.LOAD)
            dialog.isMultipleMode = false
            dialog.isVisible = true
            val dir = dialog.directory
            val name = dialog.file
            if (dir == null || name == null) null else File(dir, name).toPath()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    suspend fun pickSaveFile(
        title: String,
        defaultName: String,
        parent: Frame? = null,
    ): Path? = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(parent, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        if (dir == null || name == null) null else File(dir, name).toPath()
    }
}
