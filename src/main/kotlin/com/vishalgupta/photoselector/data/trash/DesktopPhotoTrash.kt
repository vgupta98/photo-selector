package com.vishalgupta.photoselector.data.trash

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.repository.PhotoTrash
import com.vishalgupta.photoselector.domain.repository.TrashReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Moves files to the system Trash via AWT's [Desktop.moveToTrash] (JDK 9+, backed by Finder on
 * macOS). No native interop, no `rm` shell-out — and the user retains an undo in the Trash.
 *
 * Best-effort per file: a missing file, a read-only volume, or a platform that doesn't support
 * the action is recorded against that photo and the batch carries on, so one bad file can't
 * strand the rest. Runs on [ioDispatcher] because each move is a blocking filesystem call.
 */
class DesktopPhotoTrash(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PhotoTrash {

    override suspend fun moveToTrash(photos: List<Photo>): TrashReport = withContext(ioDispatcher) {
        if (photos.isEmpty()) return@withContext TrashReport(trashed = 0, failed = emptyList())

        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        val supported = desktop != null && desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)

        var trashed = 0
        val failed = mutableListOf<Pair<Photo, Throwable>>()
        for (photo in photos) {
            coroutineContext.ensureActive()
            try {
                if (!supported) {
                    throw UnsupportedOperationException("Moving to Trash is not supported on this platform")
                }
                val moved = desktop.moveToTrash(photo.absolutePath.toFile())
                if (moved) trashed++ else failed += photo to IOException("Could not move ${photo.fileName} to Trash")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                failed += photo to t
            }
        }
        TrashReport(trashed = trashed, failed = failed)
    }
}
