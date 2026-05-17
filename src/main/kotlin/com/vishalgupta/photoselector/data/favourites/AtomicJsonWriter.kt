package com.vishalgupta.photoselector.data.favourites

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Atomically writes [bytes] to [target]: write a temp file in the same directory,
 * fsync, then atomic-move into place. A crash mid-write leaves the previous file intact.
 */
object AtomicJsonWriter {
    fun write(target: Path, bytes: ByteArray) {
        val parent = target.parent
        Files.createDirectories(parent)
        val temp = Files.createTempFile(parent, ".fav-", ".tmp")
        try {
            FileChannel.open(
                temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { ch ->
                ch.write(java.nio.ByteBuffer.wrap(bytes))
                ch.force(true)
            }
            try {
                Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedFallback) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(temp) }
        }
    }

    private object AtomicMoveNotSupportedFallback : RuntimeException()
}
