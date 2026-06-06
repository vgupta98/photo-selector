package com.vishalgupta.photoselector.domain.repository

import com.vishalgupta.photoselector.domain.model.Photo

/**
 * Outcome of a [PhotoTrash.moveToTrash] batch. [trashed] counts the files that left for the
 * recycle bin; [failed] pairs each survivor with why it stayed (unsupported platform, locked
 * file, permission). The two always sum to the requested count, so the caller can report a
 * partial result honestly rather than claiming a clean sweep.
 */
data class TrashReport(
    val trashed: Int,
    val failed: List<Pair<Photo, Throwable>>,
)

/**
 * Moves photo files to the OS recycle bin (macOS Trash). Deliberately *not* a hard delete:
 * culling is destructive enough that the user keeps an undo via the system Trash. Best-effort
 * per file — one unmovable photo never aborts the batch; it lands in [TrashReport.failed].
 */
interface PhotoTrash {
    suspend fun moveToTrash(photos: List<Photo>): TrashReport
}
