package com.vishalgupta.photoselector.data.filesystem

import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.model.ScanProgress
import com.vishalgupta.photoselector.domain.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name

class FileSystemPhotoRepository(
    private val formatRegistry: PhotoFormatRegistry,
    private val indexPersistence: IndexPersistence? = null,
) : PhotoRepository {

    override fun scan(root: RootFolder): Flow<ScanProgress> = channelFlow {
        val producer = this
        try {
            val cached = indexPersistence?.read(root)
            if (cached != null && cached.entries.isNotEmpty() && !hasDirectoryChangedSince(root, cached.scannedAtMs)) {
                val photos = indexPersistence.rebuildPhotos(root, cached)
                send(ScanProgress.Done(photos))
                return@channelFlow
            }

            val collected = ArrayList<Photo>(1024)
            var scanned = 0
            Files.walkFileTree(
                root.path,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (dir != root.path && PathFilters.isHiddenOrSystem(dir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return if (producer.isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        scanned++
                        if (PathFilters.shouldExclude(file, root)) return FileVisitResult.CONTINUE
                        if (!formatRegistry.isSupported(file)) return FileVisitResult.CONTINUE
                        val relative = root.path.relativize(file).toString().replace('\\', '/')
                        collected += Photo(
                            id = PhotoId(relative),
                            absolutePath = file,
                            relativePath = relative,
                            fileName = file.name,
                            sizeBytes = attrs.size(),
                            lastModifiedEpochMs = attrs.lastModifiedTime().toMillis(),
                        )
                        if (collected.size % 50 == 0) {
                            producer.trySend(ScanProgress.InProgress(scanned, collected.size))
                        }
                        return if (producer.isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: IOException,
                    ): FileVisitResult = FileVisitResult.CONTINUE
                },
            )
            collected.sortBy { it.relativePath }

            if (indexPersistence != null) {
                val entries = collected.map {
                    IndexEntryDto(it.relativePath, it.sizeBytes, it.lastModifiedEpochMs)
                }
                indexPersistence.write(root, entries, System.currentTimeMillis())
            }

            send(ScanProgress.Done(collected))
        } catch (t: Throwable) {
            send(ScanProgress.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    private fun hasDirectoryChangedSince(root: RootFolder, sinceMs: Long): Boolean {
        var changed = false
        Files.walkFileTree(root.path, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root.path && PathFilters.isHiddenOrSystem(dir)) return FileVisitResult.SKIP_SUBTREE
                if (attrs.lastModifiedTime().toMillis() > sinceMs) {
                    changed = true
                    return FileVisitResult.TERMINATE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes) = FileVisitResult.CONTINUE
        })
        return changed
    }
}
